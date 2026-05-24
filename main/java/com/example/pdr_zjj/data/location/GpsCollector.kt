package com.example.pdr_zjj.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.pdr_zjj.data.model.GpsSample

class GpsCollector(
    context: Context,
    private val onLocationCollected: (GpsSample) -> Unit
) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var startElapsedRealtimeNs: Long = SystemClock.elapsedRealtimeNanos()
    private var started = false
    private var lastAcceptedLocation: Location? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val filteredLocation = filterLocation(location) ?: return
            val relativeTimeSec =
                (filteredLocation.elapsedRealtimeNanos - startElapsedRealtimeNs).coerceAtLeast(0L) * 1e-9

            onLocationCollected(
                GpsSample(
                    provider = filteredLocation.provider ?: "unknown",
                    relativeTimeSec = relativeTimeSec,
                    latitudeDeg = filteredLocation.latitude,
                    longitudeDeg = filteredLocation.longitude,
                    altitudeMeter = if (filteredLocation.hasAltitude()) filteredLocation.altitude else null,
                    accuracyMeter = filteredLocation.accuracy,
                    speedMeterPerSec = if (filteredLocation.hasSpeed()) filteredLocation.speed else null,
                    bearingDeg = if (filteredLocation.hasBearing()) filteredLocation.bearing else null,
                    locationTimeMillis = filteredLocation.time,
                    systemTimeMillis = System.currentTimeMillis()
                )
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    fun resetSessionTime() {
        startElapsedRealtimeNs = SystemClock.elapsedRealtimeNanos()
        lastAcceptedLocation = null
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (started || !hasLocationPermission()) return

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                listener,
                Looper.getMainLooper()
            )
            started = true
            return
        }

        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                1000L,
                0f,
                listener,
                Looper.getMainLooper()
            )
            started = true
        }
    }

    fun stop() {
        if (!started) return
        locationManager.removeUpdates(listener)
        started = false
    }

    private fun filterLocation(location: Location): Location? {
        if (!location.hasAccuracy()) return null
        if (location.accuracy > MAX_ACCEPT_ACCURACY_METER) return null

        val previous = lastAcceptedLocation
        if (previous == null) {
            lastAcceptedLocation = Location(location)
            return Location(location)
        }

        val dtSec =
            ((location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos).coerceAtLeast(0L)) * 1e-9
        val distanceMeter = previous.distanceTo(location).toDouble()

        if (dtSec > 1e-3) {
            val measuredSpeed = distanceMeter / dtSec
            val allowedJumpMeter = maxOf(
                BASE_JUMP_METER + location.accuracy + previous.accuracy,
                MAX_PEDESTRIAN_SPEED_MPS * dtSec + location.accuracy
            )
            if (distanceMeter > allowedJumpMeter && measuredSpeed > MAX_PEDESTRIAN_SPEED_MPS) {
                return null
            }
        }

        val accuracyJumpMeter = location.accuracy - previous.accuracy
        val isSuspiciousAccuracyJump =
            accuracyJumpMeter > MAX_ALLOWED_ACCURACY_DEGRADATION_METER &&
                distanceMeter > maxOf(BASE_JUMP_METER, previous.accuracy.toDouble())

        if (isSuspiciousAccuracyJump) {
            return null
        }

        val accepted = Location(location)
        lastAcceptedLocation = Location(accepted)
        return accepted
    }

    private companion object {
        private const val MAX_ACCEPT_ACCURACY_METER = 18f
        private const val MAX_PEDESTRIAN_SPEED_MPS = 3.0
        private const val BASE_JUMP_METER = 4.0
        private const val MAX_ALLOWED_ACCURACY_DEGRADATION_METER = 6f
    }
}
