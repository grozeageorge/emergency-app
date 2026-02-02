package com.example.emergency_app.ui.emergency_contact
import java.io.File

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.emergency_app.R
import com.example.emergency_app.databinding.DialogAddContactBinding
import com.example.emergency_app.databinding.FragmentEmergencyContactBinding
import com.example.emergency_app.model.EmergencyContact
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.net.Uri
import android.widget.ImageView
import android.content.SharedPreferences

class EmergencyContactFragment : Fragment() {

    private var _binding: FragmentEmergencyContactBinding? = null
    private val binding get() = _binding!!

    private val contacts = mutableListOf<EmergencyContact>()


    private lateinit var adapter: EmergencyContactAdapter

    // Firebase
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var snapshotListener: ListenerRegistration? = null

    private lateinit var imgProfile: ImageView

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                saveImageUri(it)
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) Toast.makeText(context, "Permission Granted.", Toast.LENGTH_SHORT).show()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmergencyContactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imgProfile = binding.imgProfile

        loadProfileImage()

        imgProfile.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Initialize Firebase
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val sharedPref = requireActivity()
            .getSharedPreferences("MedicalIDPrefs", Context.MODE_PRIVATE)

        val userName = sharedPref.getString("FULL_NAME", "")

        binding.tvUserNameSubtitle.text = userName.orEmpty()

        adapter = EmergencyContactAdapter(
            contacts,
            onCallClick = { phoneNumber -> makePhoneCall(phoneNumber) },
            onDeleteClick = { contact ->
                if (contact.isEditing) {
                    Toast.makeText(context, "Save contact before deleting", Toast.LENGTH_SHORT).show()
                } else {
                    deleteContactFromFirestore(contact)
                }
            },
            onSaveClick = { contact ->
                if (contact.priority == 0) {
                    Toast.makeText(context, "Priority must be greater than 0", Toast.LENGTH_SHORT).show()
                    contact.isEditing = true
                    adapter.notifyItemChanged(contacts.indexOf(contact))
                    return@EmergencyContactAdapter
                }
                saveContactToFirestore(contact)
                Toast.makeText(context, "Contact Saved", Toast.LENGTH_SHORT).show()
            }
        )

        binding.contactsRv.layoutManager = LinearLayoutManager(requireContext())
        binding.contactsRv.adapter = adapter

        binding.btnAddContact.setOnClickListener {
            showAddContactDialog()
        }

        binding.btnAddFirstContact.setOnClickListener {
            showAddContactDialog()
        }

        setupRealtimeUpdates()
    }

    private fun updateEmptyState() {
        val isEmpty = contacts.isEmpty()
        binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.contactsRv.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.btnAddContact.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showAddContactDialog() {
        val dialogBinding = DialogAddContactBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        fun updateAddEnabled() {
            val hasName = dialogBinding.etName.text?.toString()?.trim().orEmpty().isNotEmpty()
            val hasPhone = dialogBinding.etPhone.text?.toString()?.trim().orEmpty().isNotEmpty()
            val enabled = hasName && hasPhone
            dialogBinding.btnAddContactDialog.isEnabled = enabled
            dialogBinding.btnAddContactDialog.alpha = if (enabled) 1f else 0.5f
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateAddEnabled()
            override fun afterTextChanged(s: Editable?) = Unit
        }

        dialogBinding.etName.addTextChangedListener(watcher)
        dialogBinding.etPhone.addTextChangedListener(watcher)
        updateAddEnabled()

        dialogBinding.btnCloseDialog.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnCancelDialog.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnAddContactDialog.setOnClickListener {
            val priority = dialogBinding.etPriority.text?.toString()?.toIntOrNull() ?: 1
            val contact = EmergencyContact(
                name = dialogBinding.etName.text?.toString()?.trim().orEmpty(),
                relationship = dialogBinding.etRelationship.text?.toString()?.trim().orEmpty(),
                phoneNumber = dialogBinding.etPhone.text?.toString()?.trim().orEmpty(),
                priority = priority,
                address = dialogBinding.etAddress.text?.toString()?.trim().orEmpty(),
                isEditing = false
            )
            contacts.add(contact)
            adapter.notifyItemInserted(contacts.size - 1)
            updateEmptyState()
            saveContactToFirestore(contact)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupRealtimeUpdates() {
        if (snapshotListener != null) {
            snapshotListener?.remove()
            snapshotListener = null
        }

        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(context, "User not authenticated.", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = currentUser.uid

        val contactsRef = db.collection("users").document(userId).collection("contacts")

        snapshotListener = contactsRef.orderBy("priority").addSnapshotListener { snapshot, e ->
            if (e != null) {
                // Handle error
                return@addSnapshotListener
            }

            if (snapshot != null) {
                contacts.clear()

                val loadedContacts = snapshot.toObjects(EmergencyContact::class.java)

                contacts.addAll(loadedContacts)

                adapter.notifyDataSetChanged()
                updateEmptyState()
            }
        }
    }

    private fun saveContactToFirestore(contact: EmergencyContact) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId).collection("contacts")
            .document(contact.id)
            .set(contact)
            .addOnSuccessListener {
                Toast.makeText(context, "Contact saved.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteContactFromFirestore(contact: EmergencyContact) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(context, "You must be logged in to delete contacts.", Toast.LENGTH_SHORT).show()
            return
        }
        val userId = currentUser.uid

        db.collection("users").document(userId).collection("contacts")
            .document(contact.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(context, "Contact deleted.", Toast.LENGTH_SHORT).show()
            }
    }

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "profile_image_path") {
                loadProfileImage()
            }
        }


    private fun makePhoneCall(phoneNumber: String) {
        if (phoneNumber.isBlank()) {
            Toast.makeText(context, "Enter a phone number first", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_CALL, "tel:$phoneNumber".toUri())
            startActivity(intent)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
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
                imgProfile.setImageURI(null)
                imgProfile.setImageURI(Uri.fromFile(file))
            }
        }
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


    override fun onDestroyView() {
        super.onDestroyView()
        snapshotListener?.remove()
        snapshotListener = null
        _binding = null
    }
}