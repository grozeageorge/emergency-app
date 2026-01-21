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
import com.example.emergency_app.ui.emergency_contact.EmergencyContactFragment
import com.example.emergency_app.ui.home.HomeFragment
import com.example.emergency_app.ui.medical_info.MedicalInfoFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val requiredPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.SEND_SMS,
        Manifest.permission.FOREGROUND_SERVICE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
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
        setContentView(R.layout.activity_main)
        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottomNavigationView)

        val homeFragment = HomeFragment()
        val medicalInfoFragment = MedicalInfoFragment()
        val emergencyContactFragment = EmergencyContactFragment()

        setCurrentFragment(homeFragment)

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> setCurrentFragment(homeFragment)
                R.id.medical_info -> setCurrentFragment(medicalInfoFragment)
                R.id.emergency_contact -> setCurrentFragment(emergencyContactFragment)
            }
            true
        }
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
        // Android 8.0+ requires startForegroundService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Driving Mode Started!", Toast.LENGTH_SHORT).show()
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}