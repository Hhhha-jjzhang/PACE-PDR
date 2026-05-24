package com.example.pdr_zjj.data.storage

import com.example.pdr_zjj.data.model.GpsSample
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class GpsTruthReader {

    fun read(file: File): List<GpsSample> {
        if (!file.exists()) return emptyList()

        val result = mutableListOf<GpsSample>()
        BufferedReader(FileReader(file)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val row = line.trim()
                if (row.isEmpty()) continue
                if (row.startsWith("provider", ignoreCase = true)) continue

                val parts = row.split(",")
                if (parts.size < 10) continue

                val provider = parts[0]
                val timeSec = parts[1].toDoubleOrNull() ?: continue
                val latitudeDeg = parts[2].toDoubleOrNull() ?: continue
                val longitudeDeg = parts[3].toDoubleOrNull() ?: continue
                val altitudeMeter = parts[4].toDoubleOrNull()
                val accuracyMeter = parts[5].toFloatOrNull() ?: continue
                val speedMeterPerSec = parts[6].toFloatOrNull()
                val bearingDeg = parts[7].toFloatOrNull()
                val locationTimeMillis = parts[8].toLongOrNull() ?: 0L
                val systemTimeMillis = parts[9].toLongOrNull() ?: 0L

                result.add(
                    GpsSample(
                        provider = provider,
                        relativeTimeSec = timeSec,
                        latitudeDeg = latitudeDeg,
                        longitudeDeg = longitudeDeg,
                        altitudeMeter = altitudeMeter,
                        accuracyMeter = accuracyMeter,
                        speedMeterPerSec = speedMeterPerSec,
                        bearingDeg = bearingDeg,
                        locationTimeMillis = locationTimeMillis,
                        systemTimeMillis = systemTimeMillis
                    )
                )
            }
        }

        return result
    }
}
