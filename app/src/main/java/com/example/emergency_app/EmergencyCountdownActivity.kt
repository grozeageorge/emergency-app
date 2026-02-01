package com.example.emergency_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.telephony.SmsManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.emergency_app.databinding.ActivityCountdownBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.app.NotificationManager

class EmergencyCountdownActivity : AppCompatActivity() {

    // --- DEVELOPER SWITCH ---
    // Set to TRUE to stop sending real SMS.
    // Set to FALSE before pushing to GitHub.
    private val testMode = true

    private lateinit var binding: ActivityCountdownBinding
    private var timer: CountDownTimer? = null

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val emergencyPhoneNumbers = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        binding = ActivityCountdownBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        fetchEmergencyContacts()
        startTimer()

        binding.btnCancel.setOnClickListener {
            timer?.cancel()
            Toast.makeText(this, "Alert Cancelled", Toast.LENGTH_SHORT).show()
            navigateBack()
        }
    }

    private fun fetchEmergencyContacts() {
        val currentUser = auth.currentUser ?: return

        db.collection("users")
            .document(currentUser.uid).collection("contacts")
            .get()
            .addOnSuccessListener { snapshot ->
                emergencyPhoneNumbers.clear()
                for (document in snapshot) {
                    val phone = document.getString("phoneNumber")
                    if (!phone.isNullOrEmpty()) {
                        emergencyPhoneNumbers.add(phone)
                    }
                }
            }
    }

    private fun startTimer() {
        // Reduced to 5 seconds for testing purposes (Change back to 30000 later)
        timer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvCountdown.text = (millisUntilFinished / 1000).toString()
            }

            override fun onFinish() {
                sendEmergencySMS()
                showMedicalNotification()
            }
        }.start()
    }

    private fun showMedicalNotification() {
        val sharedPref = getSharedPreferences("EmergencyLocalPrefs", MODE_PRIVATE)
        val medicalInfo = sharedPref.getString("LOCK_SCREEN_INFO", "No Medical Info Set")!!

        val channelId = "crash_detection_channel"

        val emergencyNotification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("CRASH DETECTED - MEDICAL INFO")
            .setContentText(medicalInfo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(medicalInfo))
            .setSmallIcon(android.R.drawable.ic_menu_compass)

            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()


        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, emergencyNotification)
    }

    private fun sendEmergencySMS() {
        if (!testMode && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission missing!", Toast.LENGTH_SHORT).show()
            navigateBack()
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                // 1. Send SMS (Logic handles null location inside string)
                val msg = if (location != null) {
                    "EMERGENCY! Crash detected. Location: http://maps.google.com/?q=${location.latitude},${location.longitude}"
                } else {
                    "EMERGENCY! Crash detected. GPS unavailable."
                }

                if (testMode) {
                    Toast.makeText(this, "TEST MODE: SMS Simulated", Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        val smsManager = this.getSystemService(SmsManager::class.java)
                        if (emergencyPhoneNumbers.isNotEmpty()) {
                            for (phone in emergencyPhoneNumbers) {
                                smsManager?.sendTextMessage(phone, null, msg, null, null)
                            }
                            Toast.makeText(this, "SOS SENT!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "No contacts to call!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        Toast.makeText(this, "Failed to send SMS.", Toast.LENGTH_SHORT).show()
                    }
                }

                // 2. CRITICAL FIX: Navigate back regardless of whether location was found or not
                if (location != null) {
                    navigateBackAndStartSimulation(location.latitude, location.longitude)
                } else {
                    navigateBackAndStartSimulation(0.0, 0.0)
                }
            }
            .addOnFailureListener {
                if (testMode) Toast.makeText(this, "Location failed", Toast.LENGTH_SHORT).show()
                navigateBackAndStartSimulation(0.0, 0.0)
            }
    }

    private fun navigateBack()
    {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateBackAndStartSimulation(latitude: Double, longitude: Double) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("TRIGGER_AMBULANCE", true)
        intent.putExtra("CRASH_LAT", latitude) // Pass Data
        intent.putExtra("CRASH_LON", longitude) // Pass Data
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        setResult(RESULT_OK)
        startActivity(intent)
        finish()
    }
}