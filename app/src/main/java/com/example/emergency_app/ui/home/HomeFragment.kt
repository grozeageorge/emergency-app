package com.example.emergency_app.ui.home

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.emergency_app.EmergencyCountdownActivity
import com.example.emergency_app.LoginActivity
import com.example.emergency_app.MainActivity
import com.example.emergency_app.R
import com.example.emergency_app.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.bonuspack.location.NominatimPOIProvider
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale
import androidx.core.graphics.toColorInt
import androidx.fragment.app.activityViewModels

class HomeFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Map Components
    private var locationOverlay: MyLocationNewOverlay? = null
    private var ambulanceMarker: Marker? = null
    private var routeOverlay: Polyline? = null

    // Utilities
    private var tts: TextToSpeech? = null
    private var isDrivingModeActive = false

    private val simulationViewModel: SimulationViewModel by activityViewModels()



    private var ambulanceAnimator: ValueAnimator? = null

    // Permission Launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) enableUserLocation()
        }

    // Handle return from Countdown
    private val sosResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            triggerRealAmbulanceSimulation()
        } else {
            Toast.makeText(context, "SOS Cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().load(
            requireContext(),
            requireContext().getSharedPreferences("osm_config", Context.MODE_PRIVATE)
        )
        // IMPORTANT: Set User Agent to avoid being blocked by OSM servers
        Configuration.getInstance().userAgentValue = "EmergencyApp/1.0"

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMap()
        checkLocationPermission()
        tts = TextToSpeech(requireContext(), this)

        // 1. SOS Button
        binding.btnSOS.setOnClickListener {
            val intent = Intent(requireContext(), EmergencyCountdownActivity::class.java)
            sosResultLauncher.launch(intent)
        }

        // 2. Driving Mode Button
        binding.btnDrivingMode.setOnClickListener {
            val mainActivity = activity as? MainActivity ?: return@setOnClickListener

            if (!isDrivingModeActive) {
                // Start
                mainActivity.startDrivingMode()
                isDrivingModeActive = true

                binding.btnDrivingMode.text = getString(R.string.stop_driving)
                binding.btnDrivingMode.setBackgroundColor("#D32F2F".toColorInt()) // Red
                binding.btnDrivingMode.setTextColor(Color.BLACK)
                binding.btnDrivingMode.strokeWidth = 0
                binding.btnDrivingMode.setIconResource(android.R.drawable.ic_media_pause)
                binding.btnDrivingMode.iconTint = ContextCompat.getColorStateList(requireContext(), android.R.color.black)
            } else {
                // Stop
                mainActivity.stopDrivingMode()
                isDrivingModeActive = false

                binding.btnDrivingMode.text = getString(R.string.start_driving)
                binding.btnDrivingMode.setBackgroundColor(Color.WHITE)
                binding.btnDrivingMode.setTextColor("#D32F2F".toColorInt())
                binding.btnDrivingMode.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.header_red) // Or Color.parseColor("#D32F2F")
                binding.btnDrivingMode.strokeWidth = 5
                binding.btnDrivingMode.setIconResource(R.drawable.ic_car)
                binding.btnDrivingMode.iconTint = ContextCompat.getColorStateList(requireContext(), R.color.header_red)
            }
        }

        // 3. Logout
        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireActivity(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // Check Arguments
        val shouldStart = arguments?.getBoolean("START_SIMULATION", false) ?: false
        if (shouldStart) {
            val lat = arguments?.getDouble("CRASH_LAT", 0.0) ?: 0.0
            val lon = arguments?.getDouble("CRASH_LON", 0.0) ?: 0.0

            arguments?.putBoolean("START_SIMULATION", false)

            view.postDelayed({
                // Pass the coordinates we got from Activity
                triggerRealAmbulanceSimulation(lat, lon)
            }, 1000)
        }

        if (simulationViewModel.isSimulating && simulationViewModel.activeRoad != null) {
            // Restore the map visualization without re-calculating the route
            drawRouteAndStartAnimation(
                simulationViewModel.activeRoad!!,
                simulationViewModel.duration,
                simulationViewModel.currentPlayTime
            )
        }
    }

    // --- 1. ROUTING SETUP ---
    fun triggerRealAmbulanceSimulation(lat: Double = 0.0, lon: Double = 0.0) {
        if (simulationViewModel.isSimulating) {
            Toast.makeText(context, "Crash confirmed! Ambulance is already en route.", Toast.LENGTH_SHORT).show()
            return
        }

        speak("Help is on the way. Calculating route from nearest hospital.")
        vibratePhone()

        val userLocation = if (lat != 0.0 && lon != 0.0) {
            GeoPoint(lat, lon)
        } else {
            locationOverlay?.myLocation ?: GeoPoint(44.4268, 26.1025)
        }

        lifecycleScope.launch(Dispatchers.IO) {

            // 1. Find Nearest Hospital
            val poiProvider = NominatimPOIProvider("EmergencyApp/1.0")
            val pois = try {
                poiProvider.getPOICloseTo(userLocation, "hospital", 1, 0.1)
            } catch (_: Exception) { null }

            val startPoint = if (!pois.isNullOrEmpty()) pois[0].mLocation else GeoPoint(userLocation.latitude + 0.01, userLocation.longitude + 0.01)
            val hospitalName = if (!pois.isNullOrEmpty()) pois[0].mDescription else "Dispatch Center"

            // 2. Get Road Route
            val roadManager = OSRMRoadManager(requireContext(), "EmergencyApp/1.0")
            val waypoints = arrayListOf(startPoint, userLocation)
            val road = roadManager.getRoad(waypoints)

            // --- NEW MATH: Calculate Realistic Duration ---
            // Distance is in Kilometers (e.g., 2.5 km)
            val distanceKm = road.mLength
            val speedKmh = 50.0 // Average ambulance speed in city

            // Time = Distance / Speed (in Hours)
            val timeHours = distanceKm / speedKmh

            // Convert to Milliseconds for Animator (Hours * 3600 * 1000)
            val durationMs = (timeHours * 3600 * 1000).toLong()

            // Format for Toast (e.g., "3 min")
            val minutes = (timeHours * 60).toInt()
            val etaString = if (minutes < 1) "< 1 min" else "$minutes min"

            withContext(Dispatchers.Main) {
                if (road.mStatus != Road.STATUS_OK) {
                    Toast.makeText(context, "Error finding road", Toast.LENGTH_SHORT).show()
                } else {
                    // SAVE DATA TO VIEWMODEL
                    simulationViewModel.activeRoad = road
                    simulationViewModel.duration = durationMs
                    simulationViewModel.isSimulating = true
                    simulationViewModel.currentPlayTime = 0L // Reset time for new simulation

                    // Call the visual function
                    drawRouteAndStartAnimation(road, durationMs, 0L)
                }
            }
        }
    }

    private fun drawRouteAndStartAnimation(road: Road, durationMs: Long, startTime: Long) {
        // 1. Clean up old overlays
        routeOverlay?.let { binding.map.overlays.remove(it) }
        ambulanceMarker?.let { binding.map.overlays.remove(it) }

        // 2. Draw Route
        routeOverlay = RoadManager.buildRoadOverlay(road)
        routeOverlay?.outlinePaint?.color = Color.RED
        routeOverlay?.outlinePaint?.strokeWidth = 15f
        binding.map.overlays.add(routeOverlay)

        // 3. Draw Marker
        val startPoint = road.mRouteHigh.first()
        ambulanceMarker = Marker(binding.map).apply {
            position = startPoint
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_ambulance)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            isFlat = false
        }
        binding.map.overlays.add(ambulanceMarker)

        if (startTime == 0L) {
            binding.map.zoomToBoundingBox(road.mBoundingBox, true)
        } else {
            binding.map.controller.setZoom(17.0)

            val progress = startTime.toFloat() / durationMs
            val validProgress = progress.coerceIn(0f, 1f)

            val points = road.mRouteHigh
            if (points.isNotEmpty()) {
                val index = (points.size * validProgress).toInt().coerceAtMost(points.size - 1)
                val currentPos = points[index]

                binding.map.controller.setCenter(currentPos)
            }
        }
        binding.map.invalidate()

        // 5. Start Animation logic
        animateCarAlongRoad(road, durationMs, startTime)
    }

    // --- 2. ANIMATION & TANGENT ROTATION ---
    private fun animateCarAlongRoad(road: Road, durationMs: Long, startTime: Long) {
        val routePoints = road.mRouteHigh
        if (routePoints.isEmpty()) return

        val finalDuration = if (durationMs < 3000) 3000 else durationMs

        ambulanceAnimator = ValueAnimator.ofInt(0, routePoints.size - 1)
        ambulanceAnimator?.duration = finalDuration
        ambulanceAnimator?.interpolator = LinearInterpolator()
        ambulanceAnimator?.currentPlayTime = startTime

        ambulanceAnimator?.addUpdateListener { animation ->
            if (_binding == null) {
                ambulanceAnimator?.cancel()
                return@addUpdateListener
            }
            val index = animation.animatedValue as Int
            val currentPoint = routePoints[index]

            ambulanceMarker?.position = currentPoint

            // VERY SIMPLE rotation (optional, safe)
            if (index < routePoints.size - 1) {
                val nextPoint = routePoints[index + 1]

                var bearing = currentPoint.bearingTo(nextPoint).toFloat()
                if (bearing < 0) bearing += 360f

                // Clamp rotation so it never flips
                ambulanceMarker?.rotation = bearing
            }

            binding.map.invalidate()
        }
        ambulanceAnimator?.addListener(object : android.animation.AnimatorListenerAdapter() {
            // Add a flag to track if we cancelled it manually
            var wasCancelled = false

            override fun onAnimationCancel(animation: android.animation.Animator) {
                wasCancelled = true
            }

            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (!wasCancelled) {
                    simulationViewModel.isSimulating = false
                    simulationViewModel.currentPlayTime = 0L

                    if (_binding != null) {
                        Toast.makeText(context, "Ambulance Arrived at Location", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
        ambulanceAnimator?.start()
    }

    private fun vibratePhone() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Vibrate: 500ms ON, 200ms OFF, 500ms ON (SOS Pattern-ish)
        val pattern = longArrayOf(0, 500, 200, 500)

        if (Build.VERSION.SDK_INT >= 26) {
            // -1 means "Do not repeat"
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun setupMap() {
        binding.map.setMultiTouchControls(true)
        binding.map.isTilesScaledToDpi = true
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.controller.setZoom(15.0)
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun enableUserLocation() {
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), binding.map)
        locationOverlay?.enableMyLocation()
        locationOverlay?.enableFollowLocation()
        binding.map.overlays.add(locationOverlay)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        locationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
        locationOverlay?.disableMyLocation()
    }

    override fun onDestroyView() {
        if (ambulanceAnimator != null && ambulanceAnimator!!.isRunning) {
            simulationViewModel.currentPlayTime = ambulanceAnimator!!.currentPlayTime
            simulationViewModel.isSimulating = true
            ambulanceAnimator!!.cancel()
        }

        tts?.shutdown()
        super.onDestroyView()
        _binding = null
    }

}
class SimulationViewModel : androidx.lifecycle.ViewModel() {
    var activeRoad: Road? = null
    var duration: Long = 0L
    var currentPlayTime: Long = 0L
    var isSimulating: Boolean = false
}