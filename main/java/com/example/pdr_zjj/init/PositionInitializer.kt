package com.example.pdr_zjj.init

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

class PositionInitializer(private val context: Context) {

    fun initializeRelative(): PositionInitResult {
        return PositionInitResult(
            latitudeDeg = null,
            longitudeDeg = null,
            altitudeMeter = null,
            useRelativeOrigin = true,
            success = true
        )
    }

    @SuppressLint("MissingPermission")
    fun initializeFromLocation(): PositionInitResult {
        if (!hasLocationPermission()) {
            return PositionInitResult(
                useRelativeOrigin = false,
                success = false
            )
        }

        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val gpsLocation: Location? = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            null
        }

        val networkLocation: Location? = try {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            null
        }

        val bestLocation = chooseBetterLocation(gpsLocation, networkLocation)

        return if (bestLocation != null) {
            PositionInitResult(
                latitudeDeg = bestLocation.latitude,
                longitudeDeg = bestLocation.longitude,
                altitudeMeter = bestLocation.altitude,
                useRelativeOrigin = false,
                success = true
            )
        } else {
            PositionInitResult(
                useRelativeOrigin = false,
                success = false
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    private fun chooseBetterLocation(
        gps: Location?,
        network: Location?
    ): Location? {
        if (gps == null) return network
        if (network == null) return gps

        return if (gps.accuracy <= network.accuracy) gps else network
    }
}