package com.example.emergency_app.ui.home

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.emergency_app.EmergencyCountdownActivity
import com.example.emergency_app.LoginActivity
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

    // --- NEW: Handle return from Countdown Screen ---
    private val sosResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // "SOS_SENT" - The user let the timer hit 0
            triggerAmbulanceSimulation()
        } else {
            // "CANCELLED" - The user stopped it
            Toast.makeText(context, "SOS Cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().load(
            requireContext(),
            requireContext().getSharedPreferences("osm_config", android.content.Context.MODE_PRIVATE)
        )
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMap()
        checkLocationPermission()
        tts = TextToSpeech(requireContext(), this)

        // 1. SOS Button -> Open Separate Activity
        binding.btnSOS.setOnClickListener {
            val intent = Intent(requireContext(), EmergencyCountdownActivity::class.java)
            sosResultLauncher.launch(intent)
        }

        // 3. Logout
        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireActivity(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    // --- AMBULANCE SIMULATION ---
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

        // 4. Add Marker
        ambulanceMarker = Marker(binding.map).apply {
            position = hospitalLocation
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_ambulance) // Ensure you have this icon!
            title = "Ambulance"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        binding.map.overlays.add(ambulanceMarker)

        // 5. Draw Line
        routeLine = Polyline().apply {
            addPoint(hospitalLocation)
            addPoint(userLocation)
            color = Color.RED
            width = 15f
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
            val lat = hospitalLocation.latitude + (userLocation.latitude - hospitalLocation.latitude) * fraction
            val lon = hospitalLocation.longitude + (userLocation.longitude - hospitalLocation.longitude) * fraction

            ambulanceMarker?.position = GeoPoint(lat, lon)
            binding.map.invalidate()
        }
        animator.start()
    }

    private fun vibratePhone() {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // --- STANDARD SETUP (Map, Permission, TTS) ---
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
        super.onDestroyView()
        tts?.shutdown()
        _binding = null
    }
}