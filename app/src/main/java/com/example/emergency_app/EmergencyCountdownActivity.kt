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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EmergencyCountdownActivity : AppCompatActivity() {

    private lateinit var tvCountdown: TextView
    private lateinit var btnCancel: Button
    private var timer: CountDownTimer? = null

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val emergencyPhoneNumbers = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(R.layout.activity_countdown)

        tvCountdown = findViewById(R.id.tvCountdown)
        btnCancel = findViewById(R.id.btnCancel)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        fetchEmergencyContacts()

        startTimer()

        btnCancel.setOnClickListener {
            timer?.cancel()
            Toast.makeText(this, "Alert Cancelled", Toast.LENGTH_SHORT).show()
            finish() // Close the screen
        }
    }
    private fun fetchEmergencyContacts() {
        val currentUser = auth.currentUser
        if (currentUser == null) return

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

                println("Loaded ${emergencyPhoneNumbers.size} numbers to text.")
            }
            .addOnFailureListener {
                // If fetching fails, we can't do much, maybe log it
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
            Toast.makeText(this, "Location permission missing!", Toast.LENGTH_SHORT).show()
            return
        }

        if (emergencyPhoneNumbers.isEmpty()) {
            Toast.makeText(this, "No emergency contacts found to message!", Toast.LENGTH_LONG).show()
            finish()
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

                try {
                    val smsManager =
                        this.getSystemService(SmsManager::class.java)

                    for (phone in emergencyPhoneNumbers) {
                        smsManager?.sendTextMessage(phone, null, msg, null, null)
                    }
                    Toast.makeText(this, "SOS SENT!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to send SMS.", Toast.LENGTH_SHORT).show()
                }
                setResult(RESULT_OK)
                finish()
            }
    }
}