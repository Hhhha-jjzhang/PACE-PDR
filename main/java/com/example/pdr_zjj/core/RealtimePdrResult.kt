package com.example.pdr_zjj.core

data class RealtimePdrResult(
    val rollRad: Double,
    val pitchRad: Double,
    val yawRad: Double,
    val stepResult: RealtimeStepResult?
)