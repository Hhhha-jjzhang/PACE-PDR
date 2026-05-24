package com.example.pdr_zjj.init

import android.hardware.SensorManager
import com.example.pdr_zjj.ahrs.QuaternionUtils
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class AttitudeInitializer(
    private val declinationDeg: Double = 0.0 // Magnetic declination, east-positive.
) {

    fun initialize(
        accSamples: List<FloatArray>,
        magSamples: List<FloatArray>
    ): AttitudeInitResult {

        if (accSamples.isEmpty() || magSamples.isEmpty()) {
            return AttitudeInitResult(
                rollRad = 0.0,
                pitchRad = 0.0,
                headingMagRad = 0.0,
                headingTrueRad = 0.0,
                success = false
            )
        }

        val filteredAccSamples = filterAccelerometerSamples(accSamples)
        val filteredMagSamples = filterMagnetometerSamples(magSamples)
        if (filteredAccSamples.size < MIN_REQUIRED_FILTERED_SAMPLES ||
            filteredMagSamples.size < MIN_REQUIRED_FILTERED_SAMPLES
        ) {
            return AttitudeInitResult(
                rollRad = 0.0,
                pitchRad = 0.0,
                headingMagRad = 0.0,
                headingTrueRad = 0.0,
                success = false
            )
        }

        val accMean = meanVector(filteredAccSamples)
        val magMean = meanVector(filteredMagSamples)

        val ax = accMean[0]
        val ay = accMean[1]
        val az = accMean[2]

        val mx = magMean[0]
        val my = magMean[1]
        val mz = magMean[2]

        // Estimate roll and pitch from gravity.
        val roll = atan2(ay, az)
        val pitch = atan2(-ax, sqrt(ay * ay + az * az))

        val headingCandidates = buildHeadingCandidates(
            accSamples = filteredAccSamples,
            magSamples = filteredMagSamples
        )
        val headingMag = robustCircularMean(headingCandidates) ?: computeHeadingMagRad(
            ax = ax,
            ay = ay,
            az = az,
            mx = mx,
            my = my,
            mz = mz
        ) ?: return AttitudeInitResult(
            rollRad = roll,
            pitchRad = pitch,
            headingMagRad = 0.0,
            headingTrueRad = 0.0,
            success = false
        )

        // Convert magnetic heading to true heading.
        val declinationRad = Math.toRadians(declinationDeg)
        val headingTrue = normalizeAngleRad(headingMag + declinationRad)

        return AttitudeInitResult(
            rollRad = roll,
            pitchRad = pitch,
            headingMagRad = headingMag,
            headingTrueRad = headingTrue,
            success = true
        )
    }

    private fun meanVector(samples: List<FloatArray>): DoubleArray {
        var sx = 0.0
        var sy = 0.0
        var sz = 0.0

        for (v in samples) {
            sx += v[0]
            sy += v[1]
            sz += v[2]
        }

        val n = samples.size.toDouble()
        return doubleArrayOf(sx / n, sy / n, sz / n)
    }

    private fun filterAccelerometerSamples(samples: List<FloatArray>): List<FloatArray> {
        val candidates = samples.filter { sample ->
            val norm = vectorNorm(sample)
            norm in (GRAVITY_NORM - MAX_ACC_NORM_DEVIATION)..(GRAVITY_NORM + MAX_ACC_NORM_DEVIATION)
        }
        return trimmedByNorm(candidates, trimRatio = 0.15)
    }

    private fun filterMagnetometerSamples(samples: List<FloatArray>): List<FloatArray> {
        val positiveNormSamples = samples.filter { vectorNorm(it) > 1e-6 }
        return trimmedByNorm(positiveNormSamples, trimRatio = 0.20)
    }

    private fun trimmedByNorm(samples: List<FloatArray>, trimRatio: Double): List<FloatArray> {
        if (samples.size <= 2) return samples
        val sorted = samples.sortedBy { vectorNorm(it) }
        val trimCount = (sorted.size * trimRatio).toInt()
        val fromIndex = min(trimCount, sorted.lastIndex)
        val toIndexExclusive = max(fromIndex + 1, sorted.size - trimCount)
        return sorted.subList(fromIndex, toIndexExclusive)
    }

    private fun buildHeadingCandidates(
        accSamples: List<FloatArray>,
        magSamples: List<FloatArray>
    ): List<Double> {
        val pairCount = min(accSamples.size, magSamples.size)
        if (pairCount == 0) return emptyList()

        val headingList = mutableListOf<Double>()
        for (index in 0 until pairCount) {
            val acc = accSamples[scaledIndex(index, pairCount, accSamples.size)]
            val mag = magSamples[scaledIndex(index, pairCount, magSamples.size)]
            val heading = computeHeadingMagRad(
                ax = acc[0].toDouble(),
                ay = acc[1].toDouble(),
                az = acc[2].toDouble(),
                mx = mag[0].toDouble(),
                my = mag[1].toDouble(),
                mz = mag[2].toDouble()
            ) ?: continue
            headingList += heading
        }
        return headingList
    }

    private fun robustCircularMean(angles: List<Double>): Double? {
        if (angles.isEmpty()) return null
        var mean = QuaternionUtils.circularMean(angles)
        val filtered = angles.filter {
            kotlin.math.abs(QuaternionUtils.wrapToPi(it - mean)) <= HEADING_INLIER_THRESHOLD_RAD
        }
        if (filtered.size < max(MIN_REQUIRED_HEADING_CANDIDATES, angles.size / 3)) {
            return mean
        }
        mean = QuaternionUtils.circularMean(filtered)
        return mean
    }

    private fun scaledIndex(index: Int, targetCount: Int, sourceCount: Int): Int {
        if (targetCount <= 1 || sourceCount <= 1) return 0
        val ratio = index.toDouble() / (targetCount - 1).toDouble()
        return (ratio * (sourceCount - 1)).toInt().coerceIn(0, sourceCount - 1)
    }

    private fun vectorNorm(sample: FloatArray): Double {
        return sqrt(
            sample[0].toDouble() * sample[0].toDouble() +
                sample[1].toDouble() * sample[1].toDouble() +
                sample[2].toDouble() * sample[2].toDouble()
        )
    }

    private fun computeHeadingMagRad(
        ax: Double,
        ay: Double,
        az: Double,
        mx: Double,
        my: Double,
        mz: Double
    ): Double? {
        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            inclinationMatrix,
            floatArrayOf(ax.toFloat(), ay.toFloat(), az.toFloat()),
            floatArrayOf(mx.toFloat(), my.toFloat(), mz.toFloat())
        )
        if (!success) return null

        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        return normalizeAngleRad(orientation[0].toDouble())
    }

    private fun normalizeAngleRad(angle: Double): Double {
        var a = angle
        val twoPi = 2.0 * Math.PI
        while (a < 0) a += twoPi
        while (a >= twoPi) a -= twoPi
        return a
    }

    private companion object {
        private const val GRAVITY_NORM = 9.81
        private const val MAX_ACC_NORM_DEVIATION = 1.2
        private const val MIN_REQUIRED_FILTERED_SAMPLES = 12
        private const val MIN_REQUIRED_HEADING_CANDIDATES = 8
        private val HEADING_INLIER_THRESHOLD_RAD = Math.toRadians(12.0)
    }
}
