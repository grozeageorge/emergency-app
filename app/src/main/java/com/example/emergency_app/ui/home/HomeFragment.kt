package com.example.emergency_app.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import com.example.emergency_app.LoginActivity
import com.example.emergency_app.MainActivity
import com.example.emergency_app.R
import com.example.emergency_app.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale
import androidx.core.graphics.toColorInt

class HomeFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var locationOverlay: MyLocationNewOverlay? = null

    private var countdownTimer: CountDownTimer? = null
    private var tts: TextToSpeech? = null
    private var isSOSActive = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) enableUserLocation()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // This prevents the map from being empty/white
        Configuration.getInstance().load(
            requireContext(),
            requireContext().getSharedPreferences("osm_config", android.content.Context.MODE_PRIVATE)
        )

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Init Map & Location
        setupMap()
        checkLocationPermission()

        // 2. Init Voice Engine
        tts = TextToSpeech(requireContext(), this)

        // 3. SOS Button Click -> Start Countdown
        binding.btnSOS.setOnClickListener {
            if (!isSOSActive) {
                startSOSCountdown()
            }
        }
        // 4. Cancel Button -> Stop Everything
        binding.btnCancelSOS.setOnClickListener {
            stopSOSSequence()
        }
        var isDriving = false
        binding.btnStartDriving.setOnClickListener {
            if (!isDriving) {
                (activity as? MainActivity)?.startDrivingMode()
                binding.btnStartDriving.setText(R.string.stop_driving)
                binding.btnStartDriving.setBackgroundColor("#4CAF50".toColorInt())
                isDriving = true
            } else {
                (activity as? MainActivity)?.stopDrivingMode()
                binding.btnStartDriving.setText(R.string.start_driving)
                binding.btnStartDriving.setBackgroundColor("#E0E0E0".toColorInt())
                isDriving = false
            }
        }

        // 5. Logout
        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireActivity(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        // In HomeFragment.kt inside onViewCreated or onClickListener:
        val btnStart = view.findViewById<ImageButton>(R.id.btnSOS)

        btnStart.setOnClickListener {
            // Cast activity to MainActivity to access the function
            (activity as? MainActivity)?.startDrivingMode()
        }




    }

    // --- SOS LOGIC ---

    private fun startSOSCountdown() {
        // Show the Overlay
        binding.layoutCountdown.visibility = View.VISIBLE
        binding.btnSOS.visibility = View.GONE // Hide the button while counting
        binding.btnLogout.visibility = View.GONE

        // Create a 4 second timer (tick every 1 second)
        countdownTimer = object : CountDownTimer(4000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                binding.tvCountdown.text = secondsLeft.toString()

                // Optional: Beep sound on every tick?
                // For now, silent countdown until the end
            }

            override fun onFinish() {
                // TIMER HIT ZERO!
                triggerPanicMode()
            }
        }.start()
    }

    private fun triggerPanicMode() {
        isSOSActive = true
        binding.tvCountdown.text = getString(R.string.sos)
        binding.tvCountdown.setTextColor(Color.RED)

        // Hide Cancel button? Or keep it to stop the alarm?
        // Let's keep it so user can stop the noise.
        binding.btnCancelSOS.text = getString(R.string.stop_alarm)

        // 1. Play Robotic Voice
        speak("Emergency! Emergency! Help is coming.")

        // 2. TODO: Send Location to Firebase (Next Step)

        // 3. TODO: Spawn Fake Ambulance (Next Step)
        Toast.makeText(context, "Signal Sent to Nearest Hospital!", Toast.LENGTH_LONG).show()
    }

    private fun stopSOSSequence() {
        // Stop Timer
        countdownTimer?.cancel()

        // Stop Voice
        tts?.stop()

        // Reset UI
        isSOSActive = false
        binding.layoutCountdown.visibility = View.GONE
        binding.btnSOS.visibility = View.VISIBLE
        binding.btnLogout.visibility = View.VISIBLE
        binding.tvCountdown.text = "3"
        binding.tvCountdown.setTextColor(ContextCompat.getColor(requireContext(), R.color.header_red)) // Or Color.RED
        binding.btnCancelSOS.text = getString(R.string.cancel)
    }

    // --- TEXT TO SPEECH SETUP ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // --- MAP SETUP (Existing Code) ---
    private fun setupMap() {
        binding.map.setMultiTouchControls(true)
        binding.map.isTilesScaledToDpi = true
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        val mapController = binding.map.controller
        mapController.setZoom(15.0) // User preferred zoom
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

    // --- Lifecycle Methods are Required for OSM ---
    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        locationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
        locationOverlay?.disableMyLocation()

        // Safety: Stop SOS if app is closed?
        if (isSOSActive) stopSOSSequence()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tts?.shutdown() // Kill voice engine to prevent leaks
        _binding = null
    }
}