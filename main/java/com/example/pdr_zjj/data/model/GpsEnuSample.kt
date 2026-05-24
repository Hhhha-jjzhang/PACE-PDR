package com.example.pdr_zjj.data.model

data class GpsEnuSample(
    val relativeTimeSec: Double,
    val eastMeter: Double,
    val northMeter: Double,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val accuracyMeter: Float
)
