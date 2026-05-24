package com.example.pdr_zjj.preprocess.model

data class SyncedSensorFrame(
    val relativeTimeSec: Double,
    val dtSec: Double,

    val accX: Double,
    val accY: Double,
    val accZ: Double,

    val gyroX: Double,
    val gyroY: Double,
    val gyroZ: Double,

    val magX: Double,
    val magY: Double,
    val magZ: Double
)