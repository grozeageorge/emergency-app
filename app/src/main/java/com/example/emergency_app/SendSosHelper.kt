package com.example.emergency_app

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SmsManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

object SendSosHelper {
    private val EMERGENCY_CONTACTS = listOf("0761873242")

    @SuppressLint("MissingPermission")
    fun send(context: Context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val message = if (location != null) {
                    "SOS! Crash detected. Location: http://maps.google.com/?q=${location.latitude},${location.longitude}"
                } else {
                    "SOS! Crash detected. GPS unavailable."
                }
                sendSMS(message)
            }
            .addOnFailureListener {
                sendSMS("SOS! Crash detected. GPS Failed.")
            }
    }

    private fun sendSMS(msg: String) {
        val smsManager = SmsManager.getDefault()
        for (number in EMERGENCY_CONTACTS) {
            smsManager.sendTextMessage(number, null, msg, null, null)
        }
    }
}