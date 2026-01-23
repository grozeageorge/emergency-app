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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.emergency_app.EmergencyCountdownActivity
import com.example.emergency_app.LoginActivity
import com.example.emergency_app.MainActivity
import com.example.emergency_app.R
import com.example.emergency_app.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
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
    private var routeLine: Polyline? = null

    // Utilities
    private var tts: TextToSpeech? = null
    private var isDrivingModeActive = false

    // Permission Launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) enableUserLocation()
        }

    // --- HANDLE RETURN FROM COUNTDOWN ---
    private val sosResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // If the countdown finished (SOS Sent), start the ambulance
        if (result.resultCode == Activity.RESULT_OK) {
            triggerAmbulanceSimulation()
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
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMap()
        checkLocationPermission()
        tts = TextToSpeech(requireContext(), this)

        // 1. SOS Button -> Opens the Separate Countdown Screen
        binding.btnSOS.setOnClickListener {
            val intent = Intent(requireContext(), EmergencyCountdownActivity::class.java)
            sosResultLauncher.launch(intent)
        }

        // 2. Driving Mode Button -> Toggles Service
        binding.btnDrivingMode.setOnClickListener {
            val mainActivity = activity as? MainActivity ?: return@setOnClickListener

            if (!isDrivingModeActive) {
                // --- STATE: DRIVING STARTED (Active) ---
                mainActivity.startDrivingMode()
                isDrivingModeActive = true

                // UI: Red Background, Black Text (Alert/Active Style)
                binding.btnDrivingMode.text = getString(R.string.stop_driving)
                binding.btnDrivingMode.setBackgroundColor(Color.parseColor("#D32F2F")) // Red
                binding.btnDrivingMode.setTextColor(Color.BLACK)
                binding.btnDrivingMode.strokeWidth = 0

                // Icon: Pause, Black Tint
                binding.btnDrivingMode.setIconResource(android.R.drawable.ic_media_pause)
                binding.btnDrivingMode.iconTint = ContextCompat.getColorStateList(requireContext(), android.R.color.black)
            } else {
                // --- STATE: DRIVING STOPPED (Idle) ---
                mainActivity.stopDrivingMode()
                isDrivingModeActive = false

                // UI: White Background, Red Text (Matches Logout)
                binding.btnDrivingMode.text = getString(R.string.start_driving)
                binding.btnDrivingMode.setBackgroundColor(Color.WHITE)
                binding.btnDrivingMode.setTextColor(Color.parseColor("#D32F2F")) // Red Text

                // Red Border
                binding.btnDrivingMode.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.header_red) // Or Color.parseColor("#D32F2F")
                binding.btnDrivingMode.strokeWidth = 5 // approx 2dp

                // Icon: Car, Red Tint
                binding.btnDrivingMode.setIconResource(R.drawable.ic_car)
                binding.btnDrivingMode.iconTint = ContextCompat.getColorStateList(requireContext(), R.color.header_red) // Or Color.parseColor("#D32F2F")
            }
        }

        // 3. Logout Button
        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireActivity(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    // --- AMBULANCE SIMULATION (Runs ONLY after SOS is sent) ---
    private fun triggerAmbulanceSimulation() {
        // 1. Voice & Vibrate
        speak("Help is on the way. An ambulance has been dispatched.")
        vibratePhone()

        // 2. Clean up old markers
        ambulanceMarker?.let { binding.map.overlays.remove(it) }
        routeLine?.let { binding.map.overlays.remove(it) }

        // 3. Calculate Locations
        val userLocation = locationOverlay?.myLocation ?: GeoPoint(44.4268, 26.1025)
        // Fake hospital 500m away
        val hospitalLocation = GeoPoint(userLocation.latitude + 0.005, userLocation.longitude + 0.005)

        // 4. Add Ambulance Marker
        ambulanceMarker = Marker(binding.map).apply {
            position = hospitalLocation
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_ambulance)
            title = "Ambulance"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        binding.map.overlays.add(ambulanceMarker)

        // 5. Draw Line
        routeLine = Polyline().apply {
            addPoint(hospitalLocation)
            addPoint(userLocation)
            outlinePaint.color = Color.RED
            outlinePaint.strokeWidth = 15f
        }
        binding.map.overlays.add(routeLine)

        // Zoom to show both
        binding.map.controller.animateTo(userLocation)
        binding.map.invalidate()

        // 6. Animate Movement (10 seconds)
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 10000
        animator.addUpdateListener { animation ->
            val fraction = animation.animatedValue as Float
            // Simple interpolation logic
            val lat = hospitalLocation.latitude + (userLocation.latitude - hospitalLocation.latitude) * fraction
            val lon = hospitalLocation.longitude + (userLocation.longitude - hospitalLocation.longitude) * fraction

            ambulanceMarker?.position = GeoPoint(lat, lon)
            binding.map.invalidate()
        }
        animator.start()

        Toast.makeText(context, "Ambulance dispatched!", Toast.LENGTH_LONG).show()
    }

    private fun vibratePhone() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) { // Android 12+
            val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(1000)
        }
    }

    // --- STANDARD MAP SETUP ---
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

    // --- TEXT TO SPEECH ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // --- LIFECYCLE ---
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
        super.onDestroyView()
        tts?.shutdown()
        _binding = null
    }
}