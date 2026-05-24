package com.example.pdr_zjj.data.model

data class GpsSample(
    val provider: String,
    val relativeTimeSec: Double,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeMeter: Double?,
    val accuracyMeter: Float,
    val speedMeterPerSec: Float?,
    val bearingDeg: Float?,
    val locationTimeMillis: Long,
    val systemTimeMillis: Long
)
