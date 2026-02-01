package com.example.emergency_app.ui.medical_info

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
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

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val viewModel = ViewModelProvider(requireActivity())[MedicalViewModel::class.java]

        viewModel.medicalData.observe(viewLifecycleOwner) { data ->
            if (data != null) {
                populateFields(data)
            }
        }

        setEditMode(false)

        binding.btnEdit.setOnClickListener {
            setEditMode(true)
        }

        binding.btnSave.setOnClickListener {
            saveMedicalData()
            viewModel.refresh()
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
        saveToLocalPreferences()
    }
    private fun saveToLocalPreferences() {
        val summary = "Name: ${binding.etFullName.text} | Blood: ${binding.etBloodType.text} | Allergies: ${binding.etAllergies.text
        } | Medications: ${binding.etMedications.text} | Conditions: ${binding.etConditions.text} "

        val sharedPref = requireContext().getSharedPreferences("EmergencyLocalPrefs", android.content.Context.MODE_PRIVATE)
        with (sharedPref.edit()) {
            putString("LOCK_SCREEN_INFO", summary)
            apply() // Writes to disk immediately
        }
    }
    private fun populateFields(data: Map<String, Any>) {
        binding.apply {
            // The 'data' map comes from the ViewModel (which got it from Firestore)
            // We use 'as? String' to safely convert the generic data back to text
            etFullName.setText(data["fullName"] as? String)
            etNationalId.setText(data["nationalId"] as? String)
            etGender.setText(data["gender"] as? String)
            etDob.setText(data["dob"] as? String)
            etBloodType.setText(data["bloodType"] as? String)
            etOrganDonor.setText(data["organDonor"] as? String)
            etAllergies.setText(data["allergies"] as? String)
            etMedications.setText(data["medications"] as? String)
            etConditions.setText(data["conditions"] as? String)
            etDevices.setText(data["devices"] as? String)
            etSurgery.setText(data["surgery"] as? String)
            etTestResults.setText(data["testResults"] as? String)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}