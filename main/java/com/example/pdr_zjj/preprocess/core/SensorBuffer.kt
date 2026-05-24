package com.example.pdr_zjj.preprocess.core

import com.example.pdr_zjj.preprocess.model.Vector3Sample
import java.util.ArrayDeque

class SensorBuffer(
    private val maxSize: Int = 200
) {
    private val samples = ArrayDeque<Vector3Sample>()

    fun reset() {
        samples.clear()
    }

    fun add(sample: Vector3Sample) {
        samples.addLast(sample)
        while (samples.size > maxSize) {
            samples.removeFirst()
        }
    }

    fun size(): Int = samples.size

    fun latest(): Vector3Sample? = samples.lastOrNull()

    fun interpolateAt(targetTimeSec: Double): Vector3Sample? {
        if (samples.size < 2) return null

        val list = samples.toList()

        for (i in 0 until list.size - 1) {
            val s0 = list[i]
            val s1 = list[i + 1]

            if (s0.relativeTimeSec <= targetTimeSec && targetTimeSec <= s1.relativeTimeSec) {
                return LinearInterpolation.interpolate(s0, s1, targetTimeSec)
            }
        }

        return null
    }

    fun sampleAtOrNearby(
        targetTimeSec: Double,
        maxGapSec: Double = 0.03
    ): Vector3Sample? {
        val list = samples.toList()
        if (list.isEmpty()) return null

        if (list.size >= 2) {
            val interpolated = interpolateAt(targetTimeSec)
            if (interpolated != null) return interpolated
        }

        val prev = list.lastOrNull { it.relativeTimeSec <= targetTimeSec }
        if (prev != null && (targetTimeSec - prev.relativeTimeSec) <= maxGapSec) {
            return Vector3Sample(
                relativeTimeSec = targetTimeSec,
                x = prev.x,
                y = prev.y,
                z = prev.z
            )
        }

        val next = list.firstOrNull { it.relativeTimeSec >= targetTimeSec }
        if (next != null && (next.relativeTimeSec - targetTimeSec) <= maxGapSec) {
            return Vector3Sample(
                relativeTimeSec = targetTimeSec,
                x = next.x,
                y = next.y,
                z = next.z
            )
        }

        return null
    }
}
