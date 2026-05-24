package com.example.pdr_zjj.position

import com.example.pdr_zjj.data.model.GpsEnuSample
import com.example.pdr_zjj.data.model.GpsSample
import kotlin.math.PI
import kotlin.math.cos

object GeoUtils {
    private const val EARTH_RADIUS_METER = 6378137.0

    fun toLocalEnu(
        sample: GpsSample,
        originLatitudeDeg: Double,
        originLongitudeDeg: Double
    ): GpsEnuSample {
        val latRad = Math.toRadians(sample.latitudeDeg)
        val lonRad = Math.toRadians(sample.longitudeDeg)
        val originLatRad = Math.toRadians(originLatitudeDeg)
        val originLonRad = Math.toRadians(originLongitudeDeg)

        // Local tangent-plane approximation:
        // east  > 0 : longitude increases
        // north > 0 : latitude increases
        val eastMeter =
            (lonRad - originLonRad) * cos((latRad + originLatRad) * 0.5) * EARTH_RADIUS_METER
        val northMeter = (latRad - originLatRad) * EARTH_RADIUS_METER

        return GpsEnuSample(
            relativeTimeSec = sample.relativeTimeSec,
            eastMeter = eastMeter,
            northMeter = northMeter,
            latitudeDeg = sample.latitudeDeg,
            longitudeDeg = sample.longitudeDeg,
            accuracyMeter = sample.accuracyMeter
        )
    }
}
