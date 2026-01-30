package com.example.emergency_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.emergency_app.databinding.ActivityMainBinding
import com.example.emergency_app.ui.emergency_contact.EmergencyContactFragment
import com.example.emergency_app.ui.home.HomeFragment
import com.example.emergency_app.ui.home.SimulationViewModel
import com.example.emergency_app.ui.medical_info.MedicalInfoFragment
import com.example.emergency_app.ui.medical_info.MedicalViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val homeFragment = HomeFragment()
    private val medicalInfoFragment = MedicalInfoFragment()
    private val emergencyContactFragment = EmergencyContactFragment()

    private val requiredPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.SEND_SMS,
        Manifest.permission.FOREGROUND_SERVICE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }
    }
    // Permission Launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startServiceIntent()
        } else {
            Toast.makeText(this, "Permissions needed for Crash Detection", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewModelProvider(this)[MedicalViewModel::class.java]

        setCurrentFragment(homeFragment)

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> setCurrentFragment(homeFragment)
                R.id.medical_info -> setCurrentFragment(medicalInfoFragment)
                R.id.emergency_contact -> setCurrentFragment(emergencyContactFragment)
            }
            true
        }

        checkAndTriggerAmbulance(intent)
    }
    private fun setCurrentFragment(fragment: Fragment) =
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.flFragment, fragment)
            commit()
        }

    fun startDrivingMode() {
        if (hasPermissions()) {
            startServiceIntent()
        } else {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    fun stopDrivingMode() {
        val intent = Intent(this, AccidentDetectionService::class.java)
        stopService(intent)
        Toast.makeText(this, "Driving Mode Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun startServiceIntent() {
        val intent = Intent(this, AccidentDetectionService::class.java)
        startForegroundService(intent)
        Toast.makeText(this, "Driving Mode Started!", Toast.LENGTH_SHORT).show()
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkAndTriggerAmbulance(intent)
    }

    private fun checkAndTriggerAmbulance(intent: Intent?) {
        val shouldTrigger = intent?.getBooleanExtra("TRIGGER_AMBULANCE", false) ?: false

        if (shouldTrigger) {
            // Get coordinates (Default to 0.0 if missing)
            val lat = intent.getDoubleExtra("CRASH_LAT", 0.0)
            val lon = intent.getDoubleExtra("CRASH_LON", 0.0)

            val simulationViewModel = ViewModelProvider(this)[SimulationViewModel::class.java]
            simulationViewModel.pendingSimulation = true
            simulationViewModel.pendingLat = lat
            simulationViewModel.pendingLon = lon

            binding.bottomNavigationView.selectedItemId = R.id.home
        }
    }

    fun onEmergencyFinished() {
        stopDrivingMode()
        ViewModelProvider(this)[SimulationViewModel::class.java].isDrivingModeActive = false
    }
}