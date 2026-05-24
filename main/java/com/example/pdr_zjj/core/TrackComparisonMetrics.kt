package com.example.pdr_zjj.core

data class TrackComparisonMetrics(
    val pdrPointCount: Int,
    val gpsPointCount: Int,
    val pdrPathLengthMeter: Double,
    val gpsPathLengthMeter: Double,
    val endpointErrorMeter: Double,
    val meanTrackErrorMeter: Double,
    val maxTrackErrorMeter: Double,
    val pdrClosureErrorMeter: Double,
    val gpsClosureErrorMeter: Double
)
