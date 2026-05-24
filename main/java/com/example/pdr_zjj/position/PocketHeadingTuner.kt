package com.example.pdr_zjj.position

import com.example.pdr_zjj.ahrs.QuaternionUtils

object PocketHeadingTuner {

    fun blendStepHeading(
        currentHeadingRad: Double,
        estimate: WalkingDirectionEstimator.DirectionEstimate
    ): Double {
        val alpha = when {
            estimate.confidence >= 0.92 -> 0.58
            estimate.confidence >= 0.84 -> 0.44
            else -> 0.30
        }
        return QuaternionUtils.blendYaw(currentHeadingRad, estimate.headingRad, alpha)
    }
}
