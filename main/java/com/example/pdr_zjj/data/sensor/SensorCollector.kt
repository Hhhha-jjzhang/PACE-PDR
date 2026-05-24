package com.example.pdr_zjj.data.sensor


import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.pdr_zjj.data.model.LiveSensorSnapshot
import com.example.pdr_zjj.data.model.SensorSample
import com.example.pdr_zjj.data.model.SensorType

class SensorCollector(
    context: Context,
    private val onSampleCollected: (SensorSample) -> Unit,
    private val onSnapshotUpdated: (LiveSensorSnapshot) -> Unit,
    private val samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_GAME
) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gyroSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val magSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private var startSensorTimestampNs: Long? = null
    private var latestSnapshot = LiveSensorSnapshot()

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            val values = event.values ?: return
            if (values.size < 3) return

            val sensorType = SensorType.fromAndroidType(event.sensor.type) ?: return

            if (startSensorTimestampNs == null) {
                startSensorTimestampNs = event.timestamp
            }

            val relativeTimeSec =
                (event.timestamp - (startSensorTimestampNs ?: event.timestamp)) * 1e-9

            val sample = SensorSample(
                sensorType = sensorType,
                sensorTimestampNs = event.timestamp,
                systemTimeMillis = System.currentTimeMillis(),
                relativeTimeSec = relativeTimeSec,
                x = values[0].toDouble(),
                y = values[1].toDouble(),
                z = values[2].toDouble()
            )

            onSampleCollected(sample)
            updateSnapshot(sample)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }
    }

    fun start() {
        accSensor?.let {
            sensorManager.registerListener(listener, it, samplingPeriodUs)
        }
        gyroSensor?.let {
            sensorManager.registerListener(listener, it, samplingPeriodUs)
        }
        magSensor?.let {
            sensorManager.registerListener(listener, it, samplingPeriodUs)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
    }

    fun resetSessionTime() {
        startSensorTimestampNs = null
        latestSnapshot = LiveSensorSnapshot()
        onSnapshotUpdated(latestSnapshot)
    }

    private fun updateSnapshot(sample: SensorSample) {
        latestSnapshot = when (sample.sensorType) {
            SensorType.ACCELEROMETER -> {
                latestSnapshot.copy(
                    relativeTimeSec = sample.relativeTimeSec,
                    accX = sample.x,
                    accY = sample.y,
                    accZ = sample.z,
                    hasAcc = true
                )
            }

            SensorType.GYROSCOPE -> {
                latestSnapshot.copy(
                    relativeTimeSec = sample.relativeTimeSec,
                    gyroX = sample.x,
                    gyroY = sample.y,
                    gyroZ = sample.z,
                    hasGyro = true
                )
            }

            SensorType.MAGNETOMETER -> {
                latestSnapshot.copy(
                    relativeTimeSec = sample.relativeTimeSec,
                    magX = sample.x,
                    magY = sample.y,
                    magZ = sample.z,
                    hasMag = true
                )
            }
        }

        onSnapshotUpdated(latestSnapshot)
    }
}