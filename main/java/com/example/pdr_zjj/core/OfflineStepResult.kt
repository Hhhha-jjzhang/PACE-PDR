package com.example.pdr_zjj.core

data class OfflineStepResult(
    val stepIndex: Int,
    val stepTimeSec: Double,
    val peakValue: Double,
    val stepLengthMeter: Double,
    val eastMeter: Double,
    val northMeter: Double,
    val yawRad: Double,
    val interrupted: Boolean
)