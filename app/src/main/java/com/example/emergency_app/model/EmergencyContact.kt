package com.example.emergency_app.model

data class EmergencyContact(
    val name: String,
    val phoneNumber: String,
    val relationship: String,
    val priority: Int = 0,
    val address: String? = null
)