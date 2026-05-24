package com.example.pdr_zjj.step

data class StepEvent(
    val stepIndex: Int,
    val timeSec: Double,
    val peakValue: Double,
    val valleyValue: Double,
    val amplitude: Double
)
