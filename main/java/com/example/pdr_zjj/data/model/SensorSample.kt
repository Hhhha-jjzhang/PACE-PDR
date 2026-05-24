package com.example.pdr_zjj.data.model


data class SensorSample(
    val sensorType: SensorType,
    val sensorTimestampNs: Long,
    val systemTimeMillis: Long,
    val relativeTimeSec: Double,
    val x: Double,
    val y: Double,
    val z: Double
)