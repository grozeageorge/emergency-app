package com.example.emergency_app.ui.medical_info

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.example.emergency_app.R
import com.example.emergency_app.databinding.FragmentMedicalInfoBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import android.content.Context
import android.net.Uri
import android.widget.ImageView
import java.io.File
import androidx.activity.result.contract.ActivityResultContracts
import android.content.SharedPreferences
import androidx.activity.result.ActivityResultLauncher
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.core.content.edit


class MedicalInfoFragment : Fragment() {
    private var _binding: FragmentMedicalInfoBinding? = null
    private val binding get() = _binding!!

    private lateinit var pickImageLauncher: ActivityResultLauncher<String>

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { saveImageUri(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicalInfoBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imgProfile.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        loadProfileImage()

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val viewModel = ViewModelProvider(requireActivity())[MedicalViewModel::class.java]

        viewModel.medicalData.observe(viewLifecycleOwner) { data ->
            if (data != null) {
                populateFields(data)
            }
        }

        setupExpandableSections()
        setupDatePicker()
        setEditMode(false)

        binding.btnEdit.setOnClickListener {
            setEditMode(true)
        }

        binding.btnSave.setOnClickListener {
            saveMedicalData()
            viewModel.refresh()
        }

        binding.btnCancel.setOnClickListener {
            setEditMode(false)
            viewModel.refresh()
        }
    }

    private fun setupExpandableSections() {
        setSectionExpanded(binding.identityContent, binding.identityToggle, true)
        setSectionExpanded(binding.vitalsContent, binding.vitalsToggle, true)
        setSectionExpanded(binding.criticalContent, binding.criticalToggle, true)
        setSectionExpanded(binding.historyContent, binding.historyToggle, true)

        binding.identityHeader.setOnClickListener {
            toggleSection(binding.identityContent, binding.identityToggle)
        }
        binding.vitalsHeader.setOnClickListener {
            toggleSection(binding.vitalsContent, binding.vitalsToggle)
        }
        binding.criticalHeader.setOnClickListener {
            toggleSection(binding.criticalContent, binding.criticalToggle)
        }
        binding.historyHeader.setOnClickListener {
            toggleSection(binding.historyContent, binding.historyToggle)
        }
    }

    private fun toggleSection(content: View, toggleIcon: ImageView) {
        val isExpanded = content.visibility == View.VISIBLE
        setSectionExpanded(content, toggleIcon, !isExpanded)
    }

    private fun setSectionExpanded(content: View, toggleIcon: ImageView, expanded: Boolean) {
        content.visibility = if (expanded) View.VISIBLE else View.GONE
        toggleIcon.setImageResource(
            if (expanded) R.drawable.ic_expand_less_thin else R.drawable.ic_expand_more_thin
        )
    }

    private fun setupDatePicker() {
        val openPickerIfEditable = {
            if (binding.etDob.isEnabled) {
                showDatePicker()
            }
        }

        binding.etDob.setOnClickListener { openPickerIfEditable() }
        binding.dobInputLayout.setEndIconOnClickListener { openPickerIfEditable() }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePicker = android.app.DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selected = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                binding.etDob.setText(formatter.format(selected.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun updateFieldHints(isEditing: Boolean) {
        if (isEditing) {
            binding.etFullName.hint = getString(R.string.full_name_placeholder)
            binding.etNationalId.hint = getString(R.string.national_id_placeholder)
            binding.etBloodType.hint = getString(R.string.blood_type_placeholder)
            binding.etDob.hint = getString(R.string.birth_date_placeholder)
            binding.etGender.hint = getString(R.string.gender_placeholder)
            binding.etAllergies.hint = getString(R.string.allergies_placeholder)
            binding.etMedications.hint = getString(R.string.medications_placeholder)
            binding.etConditions.hint = getString(R.string.conditions_placeholder)
            binding.etDevices.hint = getString(R.string.devices_placeholder)
            binding.etSurgery.hint = getString(R.string.surgery_placeholder)
            binding.etTestResults.hint = getString(R.string.test_results_placeholder)
        } else {
            binding.etFullName.hint = getString(R.string.not_set)
            binding.etNationalId.hint = getString(R.string.not_set)
            binding.etBloodType.hint = getString(R.string.not_set)
            binding.etDob.hint = getString(R.string.not_set)
            binding.etGender.hint = getString(R.string.not_set)
            binding.etAllergies.hint = getString(R.string.none_set)
            binding.etMedications.hint = getString(R.string.none_set)
            binding.etConditions.hint = getString(R.string.none_set)
            binding.etDevices.hint = getString(R.string.none_set)
            binding.etSurgery.hint = getString(R.string.none_set)
            binding.etTestResults.hint = getString(R.string.none_set)
        }
    }

    private fun setEditMode(isEditing: Boolean) {
        binding.apply {
            val views = listOf(
                etFullName, etNationalId, etGender, etDob, etBloodType,
                etAllergies, etMedications, etConditions,
                etDevices, etSurgery, etTestResults
            )

            views.forEach { it.isEnabled = isEditing }
            switchOrganDonor.isEnabled = isEditing
            updateFieldHints(isEditing)

            if (isEditing) {
                btnEdit.visibility = View.GONE
                saveCancelRow.visibility = View.VISIBLE
                etFullName.requestFocus()
            } else {
                btnEdit.visibility = View.VISIBLE
                saveCancelRow.visibility = View.GONE
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
            "organDonor" to if (binding.switchOrganDonor.isChecked) "Yes" else "No",
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

        val sharedPref = requireContext().getSharedPreferences("EmergencyLocalPrefs", Context.MODE_PRIVATE)
        sharedPref.edit {
            putString("LOCK_SCREEN_INFO", summary)
        }
    }
    private fun populateFields(data: Map<String, Any>) {
        binding.apply {
            etFullName.setText(data["fullName"] as? String)
            etNationalId.setText(data["nationalId"] as? String)
            etGender.setText(data["gender"] as? String)
            etDob.setText(data["dob"] as? String)
            etBloodType.setText(data["bloodType"] as? String)
            val donorValue = data["organDonor"]
            switchOrganDonor.isChecked = when (donorValue) {
                is Boolean -> donorValue
                is String -> donorValue.equals("yes", ignoreCase = true) || donorValue == "true"
                else -> false
            }
            etAllergies.setText(data["allergies"] as? String)
            etMedications.setText(data["medications"] as? String)
            etConditions.setText(data["conditions"] as? String)
            etDevices.setText(data["devices"] as? String)
            etSurgery.setText(data["surgery"] as? String)
            etTestResults.setText(data["testResults"] as? String)
        }
    }

    private fun saveImageUri(uri: Uri) {
        val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return

        val file = File(requireContext().filesDir, "profile.jpg")
        file.outputStream().use { output ->
            inputStream.copyTo(output)
        }

        requireContext()
            .getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("profile_image_path", file.absolutePath)
            .apply()
    }

    private fun loadProfileImage() {
        if (!isAdded || _binding == null || view == null) return

        val path = requireContext()
            .getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
            .getString("profile_image_path", null)

        path?.let {
            val file = File(it)
            if (file.exists()) {
                binding.imgProfile.setImageURI(null)
                binding.imgProfile.setImageURI(Uri.fromFile(file))
            }
        }
    }



    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "profile_image_path") {
                loadProfileImage()
            }
        }


    override fun onDestroyView() {
        super.onDestroyView()

        requireContext()
            .getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)

        _binding = null
    }


    override fun onResume() {
        super.onResume()
        loadProfileImage()

        requireContext()
            .getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onPause() {
        super.onPause()

        requireContext()
            .getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

}