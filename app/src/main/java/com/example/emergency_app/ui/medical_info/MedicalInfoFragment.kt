package com.example.emergency_app.ui.medical_info

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.emergency_app.databinding.FragmentMedicalInfoBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions


class MedicalInfoFragment : Fragment() {
    private var _binding: FragmentMedicalInfoBinding? = null
    private val binding get() = _binding!!

    // Firebase
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicalInfoBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        loadMedicalData()

        setEditMode(false)

        binding.btnEdit.setOnClickListener {
            setEditMode(true)
        }

        binding.btnSave.setOnClickListener {
            saveMedicalData()
        }
    }

    private fun setEditMode(isEditing: Boolean) {
        binding.apply {
            val views = listOf(
                etFullName, etNationalId, etGender, etDob, etBloodType,
                etOrganDonor, etAllergies, etMedications, etConditions,
                etDevices, etSurgery, etTestResults
            )

            views.forEach { it.isEnabled = isEditing }

            if (isEditing) {
                btnEdit.visibility = View.GONE
                btnSave.visibility = View.VISIBLE
                etFullName.requestFocus()
            } else {
                btnEdit.visibility = View.VISIBLE
                btnSave.visibility = View.GONE
            }
        }
    }

    private fun saveMedicalData() {
        val userId = auth.currentUser?.uid ?: return

        val medicalMap = hashMapOf(
            "fullName" to binding.etFullName.text.toString(),
            "nationalId" to binding.etNationalId.text.toString(),
            "gender" to binding.etGender.text.toString(),
            "dob" to binding.etDob.text.toString(),
            "bloodType" to binding.etBloodType.text.toString(),
            "organDonor" to binding.etOrganDonor.text.toString(),
            "allergies" to binding.etAllergies.text.toString(),
            "medications" to binding.etMedications.text.toString(),
            "conditions" to binding.etConditions.text.toString(),
            "devices" to binding.etDevices.text.toString(),
            "surgery" to binding.etSurgery.text.toString(),
            "testResults" to binding.etTestResults.text.toString()
        )

        db.collection("users").document(userId)
            .set(medicalMap, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(context, "Medical Info Saved", Toast.LENGTH_SHORT).show()
                setEditMode(false)
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to save data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadMedicalData() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    binding.apply {
                        etFullName.setText(document.getString("fullName") ?: "")
                        etNationalId.setText(document.getString("nationalId") ?: "")
                        etGender.setText(document.getString("gender") ?: "")
                        etDob.setText(document.getString("dob") ?: "")
                        etBloodType.setText(document.getString("bloodType") ?: "")
                        etOrganDonor.setText(document.getString("organDonor") ?: "")
                        etAllergies.setText(document.getString("allergies") ?: "")
                        etMedications.setText(document.getString("medications") ?: "")
                        etConditions.setText(document.getString("conditions") ?: "")
                        etDevices.setText(document.getString("devices") ?: "")
                        etSurgery.setText(document.getString("surgery") ?: "")
                        etTestResults.setText(document.getString("testResults") ?: "")
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to load data", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}