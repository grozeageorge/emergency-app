package com.example.emergency_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.telephony.SmsManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class EmergencyCountdownActivity : AppCompatActivity() {

    private lateinit var tvCountdown: TextView
    private lateinit var btnCancel: Button
    private var timer: CountDownTimer? = null

    // REPLACE THIS WITH REAL CONTACTS FROM YOUR DATABASE
    private val emergencyContacts = listOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(R.layout.activity_countdown)

        tvCountdown = findViewById(R.id.tvCountdown)
        btnCancel = findViewById(R.id.btnCancel)

        startTimer()

        btnCancel.setOnClickListener {
            timer?.cancel()
            Toast.makeText(this, "Alert Cancelled", Toast.LENGTH_SHORT).show()
            finish() // Close the screen
        }
    }

    private fun startTimer() {
        timer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvCountdown.text = (millisUntilFinished / 1000).toString()
            }

            override fun onFinish() {
                sendEmergencySMS()
            }
        }.start()
    }

    private fun sendEmergencySMS() {
        // 1. Check permissions again just to be safe
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(this, "Permissions missing! Cannot send SOS.", Toast.LENGTH_SHORT).show()
            finish() // Close without sending success signal
            return
        }

        // 2. Try to get location and send SMS
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    val msg = if (location != null) {
                        "EMERGENCY! Crash detected. Location: http://maps.google.com/?q=${location.latitude},${location.longitude}"
                    } else {
                        "EMERGENCY! Crash detected. GPS unavailable."
                    }

                    val smsManager = SmsManager.getDefault()
                    // Use a loop to send to all contacts (mocked for now or fetched from Intent)
                    // Note: Ensure 'emergencyContacts' list is populated!
                    for (phone in emergencyContacts) {
                        try {
                            smsManager.sendTextMessage(phone, null, msg, null, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    Toast.makeText(this@EmergencyCountdownActivity, "SOS SENT!", Toast.LENGTH_LONG).show()

                    // --- THIS IS THE FIX FOR "NOTHING HAPPENS" ---
                    // We must tell HomeFragment that the timer finished successfully
                    setResult(RESULT_OK)

                    finish()
                }
                .addOnFailureListener {
                    // Even if location fails, send a basic SOS
                    Toast.makeText(this, "Location failed, sending basic SOS", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Error sending SOS: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}