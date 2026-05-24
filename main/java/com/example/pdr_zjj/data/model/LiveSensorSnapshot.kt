package com.example.pdr_zjj.data.model


data class LiveSensorSnapshot(
    val relativeTimeSec: Double = 0.0,

    val accX: Double = 0.0,
    val accY: Double = 0.0,
    val accZ: Double = 0.0,

    val gyroX: Double = 0.0,
    val gyroY: Double = 0.0,
    val gyroZ: Double = 0.0,

    val magX: Double = 0.0,
    val magY: Double = 0.0,
    val magZ: Double = 0.0,

    val hasAcc: Boolean = false,
    val hasGyro: Boolean = false,
    val hasMag: Boolean = false
)