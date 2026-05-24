package com.example.pdr_zjj.data.model

data class AhrsPose(
    val time: Double,
    val qw: Double,
    val qx: Double,
    val qy: Double,
    val qz: Double,
    val rollRad: Double,
    val pitchRad: Double,
    val yawRad: Double
)