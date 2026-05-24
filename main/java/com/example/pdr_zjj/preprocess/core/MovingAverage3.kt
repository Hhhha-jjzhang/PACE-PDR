package com.example.pdr_zjj.preprocess.core

import java.util.ArrayDeque

class MovingAverage3(
    private val windowSize: Int
) {
    private val buffer = ArrayDeque<DoubleArray>()

    fun reset() {
        buffer.clear()
    }

    fun filter(x: Double, y: Double, z: Double): DoubleArray {
        buffer.addLast(doubleArrayOf(x, y, z))
        while (buffer.size > windowSize) {
            buffer.removeFirst()
        }

        var sx = 0.0
        var sy = 0.0
        var sz = 0.0

        for (v in buffer) {
            sx += v[0]
            sy += v[1]
            sz += v[2]
        }

        val n = buffer.size.toDouble()
        return doubleArrayOf(sx / n, sy / n, sz / n)
    }
}