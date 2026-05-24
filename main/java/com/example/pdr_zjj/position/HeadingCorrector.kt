package com.example.pdr_zjj.position

import android.hardware.SensorManager
import com.example.pdr_zjj.ahrs.QuaternionUtils
import kotlin.math.abs
import kotlin.math.sqrt

class HeadingCorrector(
    private val declinationDeg: Double = 0.0,
    private val maxAccNormDeviation: Double = 2.5
) {

    fun correctWithAccMag(
        ax: Double,
        ay: Double,
        az: Double,
        mx: Double,
        my: Double,
        mz: Double
    ): Double {
        return correctWithAccMagOrNull(
            ax = ax,
            ay = ay,
            az = az,
            mx = mx,
            my = my,
            mz = mz
        ) ?: 0.0
    }

    fun correctWithAccMagOrNull(
        ax: Double,
        ay: Double,
        az: Double,
        mx: Double,
        my: Double,
        mz: Double
    ): Double? {
        val accNorm = sqrt(ax * ax + ay * ay + az * az)
        val magNorm = sqrt(mx * mx + my * my + mz * mz)

        if (accNorm <= 1e-8 || magNorm <= 1e-8) {
            return null
        }

        if (abs(accNorm - 9.81) > maxAccNormDeviation) {
            return null
        }

        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            inclinationMatrix,
            floatArrayOf(ax.toFloat(), ay.toFloat(), az.toFloat()),
            floatArrayOf(mx.toFloat(), my.toFloat(), mz.toFloat())
        )
        if (!success) {
            return null
        }

        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val headingMag = orientation[0].toDouble()
        if (!headingMag.isFinite()) return null

        val headingTrue = QuaternionUtils.normalizeAngle0To2Pi(
            headingMag + Math.toRadians(declinationDeg)
        )
        return QuaternionUtils.displayHeadingToInternalRad(headingTrue)
    }
}
