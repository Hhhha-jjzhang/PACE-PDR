package com.example.pdr_zjj.preprocess.core

import com.example.pdr_zjj.preprocess.model.Vector3Sample

object LinearInterpolation {

    fun interpolate(
        prev: Vector3Sample,
        next: Vector3Sample,
        targetTimeSec: Double
    ): Vector3Sample {
        val t0 = prev.relativeTimeSec
        val t1 = next.relativeTimeSec

        if (t1 <= t0) {
            return Vector3Sample(
                relativeTimeSec = targetTimeSec,
                x = prev.x,
                y = prev.y,
                z = prev.z
            )
        }

        val alpha = ((targetTimeSec - t0) / (t1 - t0)).coerceIn(0.0, 1.0)

        return Vector3Sample(
            relativeTimeSec = targetTimeSec,
            x = prev.x + alpha * (next.x - prev.x),
            y = prev.y + alpha * (next.y - prev.y),
            z = prev.z + alpha * (next.z - prev.z)
        )
    }
}