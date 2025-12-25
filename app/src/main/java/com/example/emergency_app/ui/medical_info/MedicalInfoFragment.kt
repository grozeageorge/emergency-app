package com.example.emergency_app.ui.medical_info

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.emergency_app.databinding.FragmentMedicalInfoBinding
import androidx.core.content.edit


class MedicalInfoFragment : Fragment() {
    private var _binding: FragmentMedicalInfoBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMedicalInfoBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferences = requireActivity().getSharedPreferences("MedicalIDPrefs", Context.MODE_PRIVATE)

        loadData()
        setEditMode(false)

        binding.btnEdit.setOnClickListener {
            setEditMode(true)
        }

        binding.btnSave.setOnClickListener {
            saveData()
            setEditMode(false)
            Toast.makeText(context, "Medical Info Saved", Toast.LENGTH_SHORT).show()
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

    private fun saveData() {
        sharedPreferences.edit {
            binding.apply {
                putString("FULL_NAME", etFullName.text.toString())
                putString("NATIONAL_ID", etNationalId.text.toString())
                putString("GENDER", etGender.text.toString())
                putString("DOB", etDob.text.toString())
                putString("BLOOD_TYPE", etBloodType.text.toString())
                putString("ORGAN_DONOR", etOrganDonor.text.toString())
                putString("ALLERGIES", etAllergies.text.toString())
                putString("MEDICATIONS", etMedications.text.toString())
                putString("CONDITIONS", etConditions.text.toString())
                putString("DEVICES", etDevices.text.toString())
                putString("SURGERY", etSurgery.text.toString())
                putString("TEST_RESULTS", etTestResults.text.toString())
            }
        }
    }

    private fun loadData() {
        binding.apply {
            etFullName.setText(sharedPreferences.getString("FULL_NAME", ""))
            etNationalId.setText(sharedPreferences.getString("NATIONAL_ID", ""))
            etGender.setText(sharedPreferences.getString("GENDER", ""))
            etDob.setText(sharedPreferences.getString("DOB", ""))
            etBloodType.setText(sharedPreferences.getString("BLOOD_TYPE", ""))
            etOrganDonor.setText(sharedPreferences.getString("ORGAN_DONOR", ""))
            etAllergies.setText(sharedPreferences.getString("ALLERGIES", ""))
            etMedications.setText(sharedPreferences.getString("MEDICATIONS", ""))
            etConditions.setText(sharedPreferences.getString("CONDITIONS", ""))
            etDevices.setText(sharedPreferences.getString("DEVICES", ""))
            etSurgery.setText(sharedPreferences.getString("SURGERY", ""))
            etTestResults.setText(sharedPreferences.getString("TEST_RESULTS", ""))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}