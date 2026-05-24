package com.example.pdr_zjj.preprocess.core

import com.example.pdr_zjj.data.model.SensorSample
import com.example.pdr_zjj.data.model.SensorType
import com.example.pdr_zjj.preprocess.model.SyncedSensorFrame
import com.example.pdr_zjj.preprocess.model.Vector3Sample

class PreprocessEngine(
    private val accSmoothWindow: Int = 5,
    private val gyroSmoothWindow: Int = 5,
    private val magSmoothWindow: Int = 5,
    private val onFrameReady: ((SyncedSensorFrame) -> Unit)? = null
) {
    private val accBuffer = SensorBuffer(maxSize = 300)
    private val gyroBuffer = SensorBuffer(maxSize = 300)
    private val magBuffer = SensorBuffer(maxSize = 300)

    private val accFilter = MovingAverage3(accSmoothWindow)
    private val gyroFilter = MovingAverage3(gyroSmoothWindow)
    private val magFilter = MovingAverage3(magSmoothWindow)

    private var lastFrameTimeSec: Double? = null
    fun reset() {
        accBuffer.reset()
        gyroBuffer.reset()
        magBuffer.reset()

        accFilter.reset()
        gyroFilter.reset()
        magFilter.reset()
        lastFrameTimeSec = null
    }

    fun accept(sample: SensorSample): SyncedSensorFrame? {
        val v = Vector3Sample(
            relativeTimeSec = sample.relativeTimeSec,
            x = sample.x,
            y = sample.y,
            z = sample.z
        )

        when (sample.sensorType) {
            SensorType.ACCELEROMETER -> {
                accBuffer.add(v)
                return tryBuildFrameAtAccelTime(v)
            }

            SensorType.GYROSCOPE -> {
                gyroBuffer.add(v)
            }

            SensorType.MAGNETOMETER -> {
                magBuffer.add(v)
            }
        }

        return null
    }

    private fun tryBuildFrameAtAccelTime(accSample: Vector3Sample): SyncedSensorFrame? {
        val t = accSample.relativeTimeSec

        val gyroInterp = gyroBuffer.sampleAtOrNearby(t) ?: return null
        val magInterp = magBuffer.sampleAtOrNearby(t) ?: return null

        val accSmoothed = accFilter.filter(accSample.x, accSample.y, accSample.z)
        val gyroSmoothed = gyroFilter.filter(gyroInterp.x, gyroInterp.y, gyroInterp.z)
        val magSmoothed = magFilter.filter(magInterp.x, magInterp.y, magInterp.z)

        val lastT = lastFrameTimeSec
        val dtSec = if (lastT == null) 0.0 else (t - lastT).coerceAtLeast(0.0)
        lastFrameTimeSec = t

        val frame = SyncedSensorFrame(
            relativeTimeSec = t,
            dtSec = dtSec,

            accX = accSmoothed[0],
            accY = accSmoothed[1],
            accZ = accSmoothed[2],

            gyroX = gyroSmoothed[0],
            gyroY = gyroSmoothed[1],
            gyroZ = gyroSmoothed[2],

            magX = magSmoothed[0],
            magY = magSmoothed[1],
            magZ = magSmoothed[2]
        )

        onFrameReady?.invoke(frame)
        return frame
    }
}
