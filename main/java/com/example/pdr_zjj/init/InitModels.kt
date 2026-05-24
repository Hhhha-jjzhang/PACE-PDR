package com.example.pdr_zjj.init

data class AttitudeInitResult(
    val rollRad: Double,
    val pitchRad: Double,
    val headingMagRad: Double,
    val headingTrueRad: Double,
    val success: Boolean
)

data class PositionInitResult(
    val latitudeDeg: Double? = null,
    val longitudeDeg: Double? = null,
    val altitudeMeter: Double? = null,
    val useRelativeOrigin: Boolean = true,
    val success: Boolean = false
)

data class PdrInitResult(
    val attitude: AttitudeInitResult,
    val position: PositionInitResult,
    val success: Boolean
)