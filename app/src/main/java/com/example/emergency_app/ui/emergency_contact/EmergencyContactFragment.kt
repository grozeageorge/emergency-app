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
    import androidx.activity.result.contract.ActivityResultContracts
    import androidx.core.content.ContextCompat
    import androidx.fragment.app.Fragment
    import androidx.recyclerview.widget.LinearLayoutManager
    import com.example.emergency_app.databinding.FragmentEmergencyContactBinding
    import com.example.emergency_app.model.EmergencyContact
    import androidx.core.net.toUri
    import com.google.firebase.auth.FirebaseAuth
    import com.google.firebase.firestore.FirebaseFirestore
    import com.google.firebase.firestore.ListenerRegistration
    import android.net.Uri
    import android.widget.ImageView

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
                    imgProfile.setImageURI(it)
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

            loadSavedImage()

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
                val newContact = EmergencyContact(isEditing = true)
                contacts.add(newContact)
                adapter.notifyItemInserted(contacts.size - 1)
                binding.contactsRv.scrollToPosition(contacts.size - 1)
            }

            setupRealtimeUpdates()
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
                    // 1. Clear the list ONCE before adding new stuff
                    contacts.clear()

                    // 2. Convert all documents to objects
                    val loadedContacts = snapshot.toObjects(EmergencyContact::class.java)

                    // 3. Add them to your list
                    contacts.addAll(loadedContacts)

                    // 4. Refresh the whole list
                    adapter.notifyDataSetChanged()
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

        private fun loadSavedImage() {
            val path = requireContext()
                .getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
                .getString("profile_image_path", null)

            path?.let {
                val file = File(it)
                if (file.exists()) {
                    imgProfile.setImageURI(Uri.fromFile(file))
                }
            }
        }



        override fun onDestroyView() {
            super.onDestroyView()
            snapshotListener?.remove()
            snapshotListener = null
            _binding = null
        }
    }