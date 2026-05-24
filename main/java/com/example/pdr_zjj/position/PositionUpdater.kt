package com.example.pdr_zjj.position

import kotlin.math.cos
import kotlin.math.sin

class PositionUpdater {
    private var eastMeter = 0.0
    private var northMeter = 0.0

    fun reset(east0: Double = 0.0, north0: Double = 0.0) {
        eastMeter = east0
        northMeter = north0
    }

    fun update(
        timeSec: Double,
        stepLengthMeter: Double,
        yawRad: Double
    ): PdrPosition {
        // yawRad here is the heading already aligned to the local ENU frame:
        // 0 = north, clockwise positive, east = positive x, north = positive y.
        northMeter += stepLengthMeter * cos(yawRad)
        eastMeter += stepLengthMeter * sin(yawRad)

        return PdrPosition(
            timeSec = timeSec,
            eastMeter = eastMeter,
            northMeter = northMeter,
            yawRad = yawRad
        )
    }

    fun current(): PdrPosition {
        return PdrPosition(
            timeSec = 0.0,
            eastMeter = eastMeter,
            northMeter = northMeter,
            yawRad = 0.0
        )
    }
}
