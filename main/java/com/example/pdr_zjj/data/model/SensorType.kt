package com.example.pdr_zjj.data.model


enum class SensorType(val code: Int) {
    GYROSCOPE(1),
    ACCELEROMETER(2),
    MAGNETOMETER(3);

    companion object {
        fun fromAndroidType(type: Int): SensorType? {
            return when (type) {
                android.hardware.Sensor.TYPE_GYROSCOPE -> GYROSCOPE
                android.hardware.Sensor.TYPE_ACCELEROMETER -> ACCELEROMETER
                android.hardware.Sensor.TYPE_MAGNETIC_FIELD -> MAGNETOMETER
                else -> null
            }
        }
    }
}