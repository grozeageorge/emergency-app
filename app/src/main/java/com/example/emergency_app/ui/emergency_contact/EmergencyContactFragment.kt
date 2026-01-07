package com.example.emergency_app.ui.emergency_contact

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.emergency_app.databinding.FragmentEmergencyContactBinding
import com.example.emergency_app.model.EmergencyContact
import androidx.core.net.toUri

class EmergencyContactFragment : Fragment() {

    private var _binding: FragmentEmergencyContactBinding? = null
    private val binding get() = _binding!!

    private val contacts = mutableListOf<EmergencyContact>()
    private lateinit var adapter: EmergencyContactAdapter

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

        // 1. Set the Subtitle based on Medical Info
        val sharedPref = requireActivity().getSharedPreferences("MedicalIDPrefs", Context.MODE_PRIVATE)
        val userName = sharedPref.getString("FULL_NAME", "")

        binding.tvUserNameSubtitle.text = if (userName.isNullOrEmpty()) {
            "" // Hide if empty
        } else {
            userName // Show the name from Medical Tab
        }

        // 2. Clear old dummy data and ensure 1 empty contact exists
        if (contacts.isEmpty()) {
            contacts.add(EmergencyContact()) // Adds one empty card
        }

        // 3. Setup Adapter
        adapter = EmergencyContactAdapter(
            contacts,
            onCallClick = { phoneNumber -> makePhoneCall(phoneNumber) },
            onDeleteClick = { contact ->
                // Remove the item from list
                val position = contacts.indexOf(contact)
                if (position != -1 && position < contacts.size) {
                    contacts.removeAt(position)
                    adapter.notifyItemRemoved(position)
                }
            },
            onSaveClick = {
                Toast.makeText(context, "Contact Saved", Toast.LENGTH_SHORT).show()
            }
        )

        binding.contactsRv.layoutManager = LinearLayoutManager(requireContext())
        binding.contactsRv.adapter = adapter

        // 4. "Add New Contact" Button Logic
        binding.btnAddContact.setOnClickListener {
            contacts.add(EmergencyContact()) // Add empty card
            adapter.notifyItemInserted(contacts.size - 1)
            // Scroll to bottom
            binding.contactsRv.scrollToPosition(contacts.size - 1)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}