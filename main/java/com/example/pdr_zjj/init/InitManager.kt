
package com.example.pdr_zjj.init

import android.content.Context

class InitManager(
    context: Context,
    private val declinationDeg: Double = 0.0
) {
    private val attitudeInitializer = AttitudeInitializer(declinationDeg)
    private val positionInitializer = PositionInitializer(context)

    fun initializeAll(
        accSamples: List<FloatArray>,
        magSamples: List<FloatArray>,
        useGpsPosition: Boolean
    ): PdrInitResult {

        val attitudeResult = attitudeInitializer.initialize(accSamples, magSamples)

        val positionResult = if (useGpsPosition) {
            val gpsResult = positionInitializer.initializeFromLocation()
            if (gpsResult.success) gpsResult else positionInitializer.initializeRelative()
        } else {
            positionInitializer.initializeRelative()
        }

        return PdrInitResult(
            attitude = attitudeResult,
            position = positionResult,
            success = attitudeResult.success && positionResult.success
        )
    }
}