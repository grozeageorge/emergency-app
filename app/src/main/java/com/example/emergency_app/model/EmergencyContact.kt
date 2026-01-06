package com.example.emergency_app.model

import java.util.UUID

data class EmergencyContact(
    val id: String = UUID.randomUUID().toString(), // Generates a unique "fingerprint"
    var name: String = "",
    var phoneNumber: String = "",
    var relationship: String = "",
    var priority: Int = 0,
    var address: String = "",
    var isEditing: Boolean = false
)