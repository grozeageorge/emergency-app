package com.example.emergency_app.ui.home

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.example.emergency_app.EmergencyCountdownActivity
import com.example.emergency_app.LoginActivity
import com.example.emergency_app.MainActivity
import com.example.emergency_app.R
import com.example.emergency_app.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

class HomeFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Map Components
    private var locationOverlay: MyLocationNewOverlay? = null
    private var ambulanceMarker: Marker? = null
    private var routeOverlay: Polyline? = null

    // Utilities
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var pendingTtsMessage: String? = null // Queue message if TTS isn't ready

    private val simulationViewModel: SimulationViewModel by activityViewModels()
    private var ambulanceAnimator: ValueAnimator? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) enableUserLocation()
        }

    private val sosResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            triggerRealAmbulanceSimulation()
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
        Configuration.getInstance().userAgentValue = "EmergencyApp/1.0"
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMap()
        checkLocationPermission()
        tts = TextToSpeech(requireContext(), this)
        updateDrivingModeButtonUI()

        binding.btnSOS.setOnClickListener {
            val intent = Intent(requireContext(), EmergencyCountdownActivity::class.java)
            sosResultLauncher.launch(intent)
        }

        binding.btnDrivingMode.setOnClickListener {
            val mainActivity = activity as? MainActivity ?: return@setOnClickListener
            simulationViewModel.isDrivingModeActive = !simulationViewModel.isDrivingModeActive
            if (simulationViewModel.isDrivingModeActive) {
                mainActivity.startDrivingMode()
            } else {
                mainActivity.stopDrivingMode()
            }
            updateDrivingModeButtonUI()
        }

        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireActivity(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun updateDrivingModeButtonUI() {
        if (_binding == null) return
        val isActive = simulationViewModel.isDrivingModeActive
        val headerRed = ContextCompat.getColor(requireContext(), R.color.header_red)

        if (isActive) {
            binding.btnDrivingMode.text = getString(R.string.stop_driving)
            binding.btnDrivingMode.setBackgroundColor(headerRed)
            binding.btnDrivingMode.setTextColor(Color.BLACK)
            binding.btnDrivingMode.strokeWidth = 0
            binding.btnDrivingMode.setIconResource(android.R.drawable.ic_media_pause)
            binding.btnDrivingMode.iconTint = ContextCompat.getColorStateList(requireContext(), android.R.color.black)
        } else {
            binding.btnDrivingMode.text = getString(R.string.start_driving)
            binding.btnDrivingMode.setBackgroundColor(Color.WHITE)
            binding.btnDrivingMode.setTextColor(headerRed)
            binding.btnDrivingMode.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.header_red)
            binding.btnDrivingMode.strokeWidth = 5
            binding.btnDrivingMode.setIconResource(R.drawable.ic_car)
            binding.btnDrivingMode.iconTint = ContextCompat.getColorStateList(requireContext(), R.color.header_red)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        locationOverlay?.enableMyLocation()
        updateDrivingModeButtonUI()

        if (simulationViewModel.pendingSimulation) {
            simulationViewModel.pendingSimulation = false
            triggerRealAmbulanceSimulation(simulationViewModel.pendingLat, simulationViewModel.pendingLon)
        }

        // Sync state when returning to tab
        if (simulationViewModel.isSimulating && simulationViewModel.activeRoad != null) {
            val now = System.currentTimeMillis()
            val elapsedTime = now - simulationViewModel.startTimeMillis

            if (elapsedTime < simulationViewModel.duration) {
                restoreUIForEmergency()
                drawRouteAndStartAnimation(
                    simulationViewModel.activeRoad!!,
                    simulationViewModel.duration,
                    elapsedTime
                )
            } else {
                resetUI()
            }
        }
    }

    fun triggerRealAmbulanceSimulation(lat: Double = 0.0, lon: Double = 0.0) {
        if (simulationViewModel.isSimulating) return

        speak("Help is on the way. Calculating route from nearest hospital.")
        vibratePhone(isArrival = false) // Start Pattern
        restoreUIForEmergency()

        val userLocation = if (lat != 0.0 && lon != 0.0) GeoPoint(lat, lon) else locationOverlay?.myLocation ?: GeoPoint(44.4268, 26.1025)

        lifecycleScope.launch(Dispatchers.IO) {
            val poiProvider = NominatimPOIProvider("EmergencyApp/1.0")

            var startPoint = GeoPoint(userLocation.latitude + 0.01, userLocation.longitude + 0.01)

            try {
                val pois = poiProvider.getPOICloseTo(userLocation, "hospital", 10, 0.1)
                if (!pois.isNullOrEmpty()) {
                    val closest = pois.minByOrNull { it.mLocation.distanceToAsDouble(userLocation) }
                    if (closest != null) {
                        startPoint = closest.mLocation
                    }
                }
            } catch (_: Exception) { }

            val roadManager = OSRMRoadManager(requireContext(), "EmergencyApp/1.0")
            val waypoints = arrayListOf(startPoint, userLocation)
            val road = roadManager.getRoad(waypoints)

            val distanceKm = road.mLength
            val speedKmh = 50.0
            val timeHours = distanceKm / speedKmh
            val durationMs = (timeHours * 3600 * 1000).toLong()
            val minutes = (timeHours * 60).toInt()
            val etaString = if (minutes < 1) "< 1 min" else "$minutes min"

            withContext(Dispatchers.Main) {
                if (road.mStatus != Road.STATUS_OK) Toast.makeText(context, "Error finding road", Toast.LENGTH_SHORT).show()

                simulationViewModel.activeRoad = road
                simulationViewModel.duration = durationMs
                simulationViewModel.startTimeMillis = System.currentTimeMillis()
                simulationViewModel.isSimulating = true

                Toast.makeText(context, "Ambulance dispatched. ETA: $etaString", Toast.LENGTH_LONG).show()
                speak("Help is on the way. An ambulance has been dispatched.")

                drawRouteAndStartAnimation(road, durationMs, 0L)
            }
        }
    }

    private fun drawRouteAndStartAnimation(road: Road, durationMs: Long, startTime: Long) {
        routeOverlay?.let { binding.map.overlays.remove(it) }
        ambulanceMarker?.let { binding.map.overlays.remove(it) }

        routeOverlay = RoadManager.buildRoadOverlay(road)
        routeOverlay?.outlinePaint?.color = Color.RED
        routeOverlay?.outlinePaint?.strokeWidth = 15f
        binding.map.overlays.add(routeOverlay)

        val startPoint = road.mRouteHigh.first()
        ambulanceMarker = Marker(binding.map).apply {
            position = startPoint
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_ambulance)
            title = "Ambulance"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            isFlat = true
        }
        binding.map.overlays.add(ambulanceMarker)

        if (startTime == 0L) {
            binding.map.zoomToBoundingBox(road.mBoundingBox, true)
        } else {
            binding.map.controller.setZoom(17.0)
        }
        binding.map.invalidate()

        animateCarAlongRoad(road, durationMs, startTime)
    }

    private fun animateCarAlongRoad(road: Road, durationMs: Long, startTime: Long) {
        val routePoints = road.mRouteHigh
        if (routePoints.isEmpty()) return

        ambulanceAnimator?.cancel()

        ambulanceAnimator = ValueAnimator.ofInt(0, routePoints.size - 1)
        ambulanceAnimator?.duration = durationMs
        ambulanceAnimator?.interpolator = LinearInterpolator()
        ambulanceAnimator?.currentPlayTime = startTime

        ambulanceAnimator?.addUpdateListener { animation ->
            if (_binding == null) return@addUpdateListener
            val index = animation.animatedValue as Int
            val currentPoint = routePoints[index]
            ambulanceMarker?.position = currentPoint

            var lookAheadIndex = index + 1
            while (lookAheadIndex < routePoints.size) {
                val nextPoint = routePoints[lookAheadIndex]
                val distanceMeters = currentPoint.distanceToAsDouble(nextPoint)
                if (distanceMeters > 15) {
                    var bearing = currentPoint.bearingTo(nextPoint).toFloat()
                    if (bearing > 180) bearing -= (bearing - 180) * 2
                    ambulanceMarker?.rotation = bearing + 180f
                    break
                }
                lookAheadIndex++
            }
            binding.map.invalidate()
        }

        ambulanceAnimator?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!simulationViewModel.isSimulating) return

                // --- ARRIVAL LOGIC ---
                Toast.makeText(context, "Ambulance Arrived!", Toast.LENGTH_LONG).show()

                speak("The ambulance has arrived at your location.")
                vibratePhone(isArrival = true)

                lifecycleScope.launch {
                    delay(10000)
                    resetUI()
                }
            }
        })

        ambulanceAnimator?.start()
    }

    private fun restoreUIForEmergency() {
        binding.btnSOS.visibility = View.GONE
        binding.btnDrivingMode.visibility = View.GONE
        binding.btnLogout.visibility = View.GONE
    }

    private fun resetUI() {
        if (_binding == null) return

        ambulanceAnimator?.cancel()
        routeOverlay?.let { binding.map.overlays.remove(it) }
        ambulanceMarker?.let { binding.map.overlays.remove(it) }
        binding.map.invalidate()

        simulationViewModel.isSimulating = false
        simulationViewModel.activeRoad = null
        simulationViewModel.startTimeMillis = 0L

        binding.btnSOS.visibility = View.VISIBLE
        binding.btnDrivingMode.visibility = View.VISIBLE
        binding.btnLogout.visibility = View.VISIBLE

        (activity as? MainActivity)?.onEmergencyFinished()
    }

    private fun vibratePhone(isArrival: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Pattern: [Delay, Vibrate, Sleep, Vibrate...]
        // SOS / Start: Long pulses
        val startPattern = longArrayOf(0, 500, 200, 500)
        // Arrival: 3 Short pulses
        val arrivalPattern = longArrayOf(0, 200, 100, 200, 100, 200)

        val pattern = if (isArrival) arrivalPattern else startPattern

        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    // --- STANDARD SETUP ---
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

    // --- TTS LOGIC WITH QUEUE ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
            // If a message tried to play while initializing, play it now
            pendingTtsMessage?.let {
                speak(it)
                pendingTtsMessage = null
            }
        }
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            pendingTtsMessage = text // Queue it for onInit
        }
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
        locationOverlay?.disableMyLocation()
        ambulanceAnimator?.pause()
    }

    override fun onDestroyView() {
        ambulanceAnimator?.removeAllListeners()
        ambulanceAnimator?.cancel()
        tts?.shutdown()
        super.onDestroyView()
        _binding = null
    }
}

class SimulationViewModel : ViewModel() {
    var activeRoad: Road? = null
    var duration: Long = 0L
    var startTimeMillis: Long = 0L
    var isSimulating: Boolean = false
    var isDrivingModeActive: Boolean = false

    var pendingSimulation: Boolean = false
    var pendingLat: Double = 0.0
    var pendingLon: Double = 0.0
}