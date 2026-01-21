package com.example.emergency_app.ui.medical_info

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MedicalViewModel : ViewModel() {

    // This holds the data so the UI can observe it
    private val _medicalData = MutableLiveData<Map<String, Any>?>()
    val medicalData: LiveData<Map<String, Any>?> = _medicalData


    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    init {
        // Automatically fetch data as soon as this ViewModel is created (App Start)
        fetchMedicalData()
    }

    fun fetchMedicalData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Save the data to our LiveData
                    _medicalData.value = document.data
                }
            }
    }

    // We can allow the Fragment to force a refresh if needed
    fun refresh() {
        fetchMedicalData()
    }
}