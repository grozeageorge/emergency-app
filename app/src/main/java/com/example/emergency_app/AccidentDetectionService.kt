package com.example.emergency_app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlin.math.sqrt

class AccidentDetectionService : Service(), SensorEventListener {


    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private val crashThreshold = 5.0

    override fun onCreate() {
        super.onCreate()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        startForegroundServiceNotification()

        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Calculate G-Force
            val gForce = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH

            if (gForce > crashThreshold) {
                sensorManager.unregisterListener(this)
                triggerEmergencyCountdown()
            }
        }
    }

    private fun triggerEmergencyCountdown() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                startCountdownActivity(location?.latitude ?: 0.0, location?.longitude ?: 0.0)
            }.addOnFailureListener {
                startCountdownActivity(0.0, 0.0)
            }
        } else {
            startCountdownActivity(0.0, 0.0)
        }
    }

    private fun startCountdownActivity(lat: Double, lon: Double) {
        val intent = Intent(this, EmergencyCountdownActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("CRASH_LAT", lat)
            putExtra("CRASH_LON", lon)
        }
        startActivity(intent)
        //stopSelf()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "crash_detection_channel"
        val channelName = "Accident Detection"

        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Driving Mode Active")
            .setContentText("Monitoring for accidents...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }
}