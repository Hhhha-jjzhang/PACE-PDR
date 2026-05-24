package com.example.pdr_zjj.data.storage

import com.example.pdr_zjj.data.model.SensorSample
import com.example.pdr_zjj.data.model.SensorType
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class CompatibleRawDataReader {

    fun read(file: File): List<SensorSample> {
        if (!file.exists()) return emptyList()

        val result = mutableListOf<SensorSample>()

        BufferedReader(FileReader(file)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val row = line.trim()
                if (row.isEmpty()) continue
                if (row.startsWith("type", ignoreCase = true)) continue

                val parts = row.split(",")
                if (parts.size < 7) continue

                val typeCode = parts[0].toIntOrNull() ?: continue
                val relativeTimeSec = parts[1].toDoubleOrNull() ?: continue
                val x = parts[2].toDoubleOrNull() ?: continue
                val y = parts[3].toDoubleOrNull() ?: continue
                val z = parts[4].toDoubleOrNull() ?: continue
                val sensorTimestampNs = parts[5].toLongOrNull() ?: 0L
                val systemTimeMillis = parts[6].toLongOrNull() ?: 0L

                val sensorType = when (typeCode) {
                    1 -> SensorType.GYROSCOPE
                    2 -> SensorType.ACCELEROMETER
                    3 -> SensorType.MAGNETOMETER
                    else -> continue
                }

                result.add(
                    SensorSample(
                        sensorType = sensorType,
                        sensorTimestampNs = sensorTimestampNs,
                        systemTimeMillis = systemTimeMillis,
                        relativeTimeSec = relativeTimeSec,
                        x = x,
                        y = y,
                        z = z
                    )
                )
            }
        }

        return result
    }
}
