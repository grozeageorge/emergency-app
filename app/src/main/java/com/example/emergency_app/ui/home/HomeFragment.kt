package com.example.emergency_app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import com.example.emergency_app.LoginActivity
import com.example.emergency_app.MainActivity
import com.example.emergency_app.R
import com.example.emergency_app.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // This prevents the map from being empty/white
        Configuration.getInstance().load(
            requireContext(),
            requireContext().getSharedPreferences("osm_config", android.content.Context.MODE_PRIVATE)
        )

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMap()

        // Logout Logic
        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireActivity(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        // In HomeFragment.kt inside onViewCreated or onClickListener:
        val btnStart = view.findViewById<ImageButton>(R.id.btnSOS)

        btnStart.setOnClickListener {
            // Cast activity to MainActivity to access the function
            (activity as? MainActivity)?.startDrivingMode()
        }

        // (We will add SOS button logic here later)
    }

    private fun setupMap() {
        // 1. Enable Zoom Controls (Pinch to zoom)
        binding.map.setMultiTouchControls(true)

        // 2. Set the Map Style (MAPNIK is the standard street view)
        binding.map.setTileSource(TileSourceFactory.MAPNIK)

        // 3. Set Default View
        val mapController = binding.map.controller
        mapController.setZoom(15.0)

        // Default Start Point: Bucharest (We will change this to Real GPS later)
        val startPoint = GeoPoint(44.4268, 26.1025)
        mapController.setCenter(startPoint)
    }

    // --- Lifecycle Methods are Required for OSM ---
    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}