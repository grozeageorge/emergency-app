package com.example.emergency_app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.emergency_app.ui.emergency_contact.EmergencyContactFragment
import com.example.emergency_app.ui.home.HomeFragment
import com.example.emergency_app.ui.medical_info.MedicalInfoFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottomNavigationView)

        val homeFragment = HomeFragment()
        val medicalInfoFragment = MedicalInfoFragment()
        val emergencyContactFragment = EmergencyContactFragment()

        setCurrentFragment(homeFragment)

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> setCurrentFragment(homeFragment)
                R.id.medical_info -> setCurrentFragment(medicalInfoFragment)
                R.id.emergency_contact -> setCurrentFragment(emergencyContactFragment)
            }
            true
        }
    }
    private fun setCurrentFragment(fragment: Fragment) =
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.flFragment, fragment)
            commit()
        }
}