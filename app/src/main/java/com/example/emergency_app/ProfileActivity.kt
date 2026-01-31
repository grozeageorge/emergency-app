package com.example.emergency_app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class ProfileActivity : AppCompatActivity() {

    private lateinit var imgProfile: ImageView

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                // 🔒 păstrează accesul PERMANENT
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                imgProfile.setImageURI(it)
                saveImageUri(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_emergency_contact)

        imgProfile = findViewById(R.id.imgProfile)

        loadSavedImage()

        imgProfile.setOnClickListener {
            openGallery()
        }
    }

    private fun openGallery() {
        pickImageLauncher.launch(arrayOf("image/*"))
    }

    private fun saveImageUri(uri: Uri) {
        val prefs = getSharedPreferences("profile_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString("profile_image_uri", uri.toString())
            .apply()
    }

    private fun loadSavedImage() {
        val prefs = getSharedPreferences("profile_prefs", MODE_PRIVATE)
        val uriString = prefs.getString("profile_image_uri", null)
        uriString?.let {
            imgProfile.setImageURI(Uri.parse(it))
        }
    }
}