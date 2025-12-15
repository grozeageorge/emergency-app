package com.example.emergency_app.ui.emergency_contact

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.emergency_app.R
import com.example.emergency_app.databinding.FragmentEmergencyContactBinding
import com.example.emergency_app.model.EmergencyContact



class EmergencyContactFragment : Fragment() {

    private var _binding: FragmentEmergencyContactBinding? = null
    private val binding get() = _binding!!

    private val contacts = mutableListOf<EmergencyContact>()
    private lateinit var adapter: EmergencyContactAdapter

    private var isDeleteMode = false;

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(context, "Permission Granted. Press call again.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Permission Denied.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentEmergencyContactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.username.text = "John Doe"

        if (contacts.isEmpty()) {
            contacts.add(EmergencyContact("Jane Smith", "123-456-7890", "Sister", 1, "123 Main St"))
            contacts.add(EmergencyContact("Mike Johnson", "987-654-3210", "Father", 2, "456 Elm St"))
        }


        adapter = EmergencyContactAdapter(
            items = contacts,
            onCallClick = { phoneNumber -> makePhoneCall(phoneNumber) },
            onItemClick = { contact -> handleContactClick(contact) }
        )

        binding.contactsRv.layoutManager = LinearLayoutManager(requireContext())
        binding.contactsRv.adapter = adapter

        binding.btnAddContact.setOnClickListener {
            showAddContactDialog()
        }

        binding.btnDeleteContact.setOnClickListener {
            activateDeleteMode()
        }
    }

    private fun activateDeleteMode() {
        isDeleteMode = true
        Toast.makeText(requireContext(), "Tap on a contact to delete.", Toast.LENGTH_LONG).show()
    }

    private fun handleContactClick(contact: EmergencyContact) {
        if (isDeleteMode) {
            contacts.remove(contact)
            adapter.notifyDataSetChanged()
            Toast.makeText(requireContext(), "Deleted ${contact.name}.", Toast.LENGTH_SHORT).show()
            isDeleteMode = false
        } else {
            // for now do nothing
        }
    }

    private fun showAddContactDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_contact, null)
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etPhone)
        val etRelationship = dialogView.findViewById<EditText>(R.id.etRelationship)
        val etPriority = dialogView.findViewById<EditText>(R.id.etPriority)
        val etAddress = dialogView.findViewById<EditText>(R.id.etAddress)


        AlertDialog.Builder(requireContext())
            .setTitle("Add Emergency Contact")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString()
                val phone = etPhone.text.toString()
                val relationship = etRelationship.text.toString()
                val priorityStr = etPriority.text.toString()
                val address = etAddress.text.toString()

                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    val priority = priorityStr.toIntOrNull() ?: 0
                    val address = address.ifEmpty { null }

                    val newContact = EmergencyContact(name = name, phoneNumber = phone, relationship = relationship, priority = priority, address = address)
                    contacts.add(newContact)
                    adapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(context, "Name and phone field required.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun makePhoneCall(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$phoneNumber")
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