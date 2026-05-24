package com.example.pdr_zjj.core

import com.example.pdr_zjj.ahrs.AhrsMode
import com.example.pdr_zjj.mode.PdrMode

object PdrConfigFactory {

    fun realtime(
        ahrsMode: AhrsMode,
        heightMeter: Double = 1.72,
        declinationDeg: Double = 0.0
    ): PdrConfig {
        val is6Dof = ahrsMode == AhrsMode.DOF6
        return PdrConfig(
            mode = PdrMode.REALTIME,
            ahrsMode = ahrsMode,
            accSmoothWindow = 5,
            gyroSmoothWindow = 5,
            magSmoothWindow = 7,
            initAccSampleCount = 120,
            initMagSampleCount = 80,
            stepBaseThreshold = if (is6Dof) 0.20 else 0.22,
            stepDynamicThresholdFactor = if (is6Dof) 0.42 else 0.46,
            stepMinPeakToValley = if (is6Dof) 0.08 else 0.09,
            minStepIntervalSec = if (is6Dof) 0.32 else 0.40,
            stepDetectionThresholdScale = if (is6Dof) 1.00 else 1.30,
            stepBaseLengthFactor = if (is6Dof) 0.31 else 0.32,
            stepCadenceFactor = if (is6Dof) 0.08 else 0.08,
            stepAmplitudeFactor = if (is6Dof) 0.12 else 0.10,
            stepLengthScaleFactor = if (is6Dof) 0.68 else 0.58,
            minStepLengthMeter = if (is6Dof) 0.30 else 0.32,
            maxStepLengthMeter = if (is6Dof) 0.78 else 0.88,
            interruptThresholdSec = 1.5,
            headingCorrectionBlendFactor = 0.55,
            heightMeter = heightMeter,
            declinationDeg = declinationDeg,
            useGpsPosition = false
        )
    }

    fun offline(
        ahrsMode: AhrsMode,
        heightMeter: Double = 1.72,
        declinationDeg: Double = 0.0
    ): PdrConfig {
        val is6Dof = ahrsMode == AhrsMode.DOF6
        return PdrConfig(
            mode = PdrMode.OFFLINE,
            ahrsMode = ahrsMode,
            accSmoothWindow = 5,
            gyroSmoothWindow = 7,
            magSmoothWindow = 9,
            initAccSampleCount = 150,
            initMagSampleCount = 100,
            stepBaseThreshold = if (is6Dof) 0.18 else 0.18,
            stepDynamicThresholdFactor = if (is6Dof) 0.38 else 0.40,
            stepMinPeakToValley = if (is6Dof) 0.07 else 0.07,
            minStepIntervalSec = if (is6Dof) 0.30 else 0.31,
            stepDetectionThresholdScale = if (is6Dof) 1.00 else 1.26,

            stepBaseLengthFactor = if (is6Dof) 0.30 else 0.39,
            stepCadenceFactor = if (is6Dof) 0.07 else 0.08,
            stepAmplitudeFactor = if (is6Dof) 0.10 else 0.10,
            stepLengthScaleFactor = if (is6Dof) 0.72 else 0.96,
            minStepLengthMeter = if (is6Dof) 0.28 else 0.35,
            maxStepLengthMeter = if (is6Dof) 0.72 else 1.02,
            interruptThresholdSec = 1.5,
            headingCorrectionBlendFactor = 0.50,
            heightMeter = heightMeter,
            declinationDeg = declinationDeg,
            useGpsPosition = false
        )
    }
}
