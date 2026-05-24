package com.example.pdr_zjj.core

import com.example.pdr_zjj.ahrs.AhrsMode
import com.example.pdr_zjj.mode.PdrMode

data class PdrConfig(
    val mode: PdrMode,
    val ahrsMode: AhrsMode,
    val accSmoothWindow: Int,
    val gyroSmoothWindow: Int,
    val magSmoothWindow: Int,
    val initAccSampleCount: Int,
    val initMagSampleCount: Int,
    val stepBaseThreshold: Double,
    val stepDynamicThresholdFactor: Double,
    val stepMinPeakToValley: Double,
    val minStepIntervalSec: Double,
    val stepDetectionThresholdScale: Double,
    val stepBaseLengthFactor: Double,
    val stepCadenceFactor: Double,
    val stepAmplitudeFactor: Double,
    val stepLengthScaleFactor: Double,
    val minStepLengthMeter: Double,
    val maxStepLengthMeter: Double,
    val interruptThresholdSec: Double,
    val headingCorrectionBlendFactor: Double,
    val heightMeter: Double,
    val declinationDeg: Double,
    val useGpsPosition: Boolean
)
