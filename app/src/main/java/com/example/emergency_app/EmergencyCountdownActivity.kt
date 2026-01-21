package com.example.emergency_app

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
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
    private val emergencyContacts = listOf("0761873242")

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
        timer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvCountdown.text = (millisUntilFinished / 1000).toString()
            }

            override fun onFinish() {
                sendEmergencySMS()
            }
        }.start()
    }

    private fun sendEmergencySMS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val msg = if (location != null) {
                    "EMERGENCY! Crash detected. Location: http://maps.google.com/?q=${location.latitude},${location.longitude}"
                } else {
                    "EMERGENCY! Crash detected. GPS unavailable."
                }

                val smsManager = SmsManager.getDefault()
                for (phone in emergencyContacts) {
                    smsManager.sendTextMessage(phone, null, msg, null, null)
                }
                Toast.makeText(this@EmergencyCountdownActivity, "SOS SENT!", Toast.LENGTH_LONG).show()
                finish()
            }
    }
}