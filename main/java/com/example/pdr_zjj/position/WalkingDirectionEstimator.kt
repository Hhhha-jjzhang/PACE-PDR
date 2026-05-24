package com.example.pdr_zjj.position

import com.example.pdr_zjj.ahrs.Quaternion
import com.example.pdr_zjj.ahrs.QuaternionUtils
import kotlin.math.abs
import kotlin.math.atan2

class WalkingDirectionEstimator(
    private val gravityMeterPerSec2: Double = 9.81
) {
    private data class HorizontalSample(
        val eastMeterPerSec2: Double,
        val northMeterPerSec2: Double,
        val energy: Double,
        val tiltRad: Double
    )

    data class DirectionEstimate(
        val headingRad: Double,
        val confidence: Double,
        val pocketLikely: Boolean
    )

    private val samples = ArrayDeque<HorizontalSample>()

    fun reset() {
        samples.clear()
    }

    fun addFrame(
        quaternion: Quaternion,
        ax: Double,
        ay: Double,
        az: Double,
        rollRad: Double,
        pitchRad: Double
    ) {
        val gravityBody = QuaternionUtils.gravityUnitBody(quaternion)
        val linearBodyX = ax - gravityBody.first * gravityMeterPerSec2
        val linearBodyY = ay - gravityBody.second * gravityMeterPerSec2
        val linearBodyZ = az - gravityBody.third * gravityMeterPerSec2

        val worldLinear = QuaternionUtils.rotateBodyToWorld(
            q = quaternion,
            x = linearBodyX,
            y = linearBodyY,
            z = linearBodyZ
        )

        val east = worldLinear.first
        val north = worldLinear.second
        val energy = east * east + north * north
        val tilt = maxOf(abs(rollRad), abs(pitchRad))

        samples.addLast(
            HorizontalSample(
                eastMeterPerSec2 = east,
                northMeterPerSec2 = north,
                energy = energy,
                tiltRad = tilt
            )
        )
        while (samples.size > MAX_WINDOW_SIZE) {
            samples.removeFirst()
        }
    }

    fun estimateHeading(referenceHeadingRad: Double): DirectionEstimate? {
        if (samples.size < MIN_SAMPLE_COUNT) return null

        val meanEnergy = samples.map { it.energy }.average()
        if (meanEnergy < MIN_HORIZONTAL_ENERGY) return null

        val meanTilt = samples.map { it.tiltRad }.average()
        val pocketLikely = meanTilt >= POCKET_TILT_THRESHOLD_RAD
        if (!pocketLikely) return null

        var weightedXX = 0.0
        var weightedYY = 0.0
        var weightedXY = 0.0
        var weightSum = 0.0

        for (sample in samples) {
            val weight = sample.energy.coerceAtLeast(MIN_SAMPLE_WEIGHT)
            weightedXX += sample.eastMeterPerSec2 * sample.eastMeterPerSec2 * weight
            weightedYY += sample.northMeterPerSec2 * sample.northMeterPerSec2 * weight
            weightedXY += sample.eastMeterPerSec2 * sample.northMeterPerSec2 * weight
            weightSum += weight
        }

        if (weightSum <= 1e-9) return null

        val xx = weightedXX / weightSum
        val yy = weightedYY / weightSum
        val xy = weightedXY / weightSum

        val trace = xx + yy
        val diff = xx - yy
        val root = kotlin.math.sqrt(diff * diff + 4.0 * xy * xy)
        val lambda1 = (trace + root) * 0.5
        val lambda2 = (trace - root) * 0.5
        val anisotropy = if (lambda1 <= 1e-9) 0.0 else ((lambda1 - lambda2) / lambda1).coerceIn(0.0, 1.0)
        if (anisotropy < MIN_ANISOTROPY) return null

        val axisHeading = QuaternionUtils.normalizeAngle0To2Pi(
            atan2(
                xx - lambda2,
                xy
            )
        )
        val oppositeHeading = QuaternionUtils.normalizeAngle0To2Pi(axisHeading + Math.PI)
        val chosenHeading = chooseNearestHeading(
            referenceHeadingRad = referenceHeadingRad,
            candidateA = axisHeading,
            candidateB = oppositeHeading
        )

        return DirectionEstimate(
            headingRad = chosenHeading,
            confidence = anisotropy,
            pocketLikely = true
        )
    }

    private fun chooseNearestHeading(
        referenceHeadingRad: Double,
        candidateA: Double,
        candidateB: Double
    ): Double {
        val deltaA = abs(QuaternionUtils.wrapToPi(candidateA - referenceHeadingRad))
        val deltaB = abs(QuaternionUtils.wrapToPi(candidateB - referenceHeadingRad))
        return if (deltaA <= deltaB) candidateA else candidateB
    }

    private companion object {
        private const val MAX_WINDOW_SIZE = 48
        private const val MIN_SAMPLE_COUNT = 20
        private const val MIN_HORIZONTAL_ENERGY = 0.12
        private const val MIN_SAMPLE_WEIGHT = 0.05
        private const val MIN_ANISOTROPY = 0.72
        private val POCKET_TILT_THRESHOLD_RAD = Math.toRadians(38.0)
    }
}
