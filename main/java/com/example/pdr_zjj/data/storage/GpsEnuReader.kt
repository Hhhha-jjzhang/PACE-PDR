package com.example.pdr_zjj.data.storage

import com.example.pdr_zjj.data.model.GpsEnuSample
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class GpsEnuReader {

    fun read(file: File): List<GpsEnuSample> {
        if (!file.exists()) return emptyList()

        val result = mutableListOf<GpsEnuSample>()
        BufferedReader(FileReader(file)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val row = line.trim()
                if (row.isEmpty()) continue
                if (row.startsWith("time_sec", ignoreCase = true)) continue

                val parts = row.split(",")
                if (parts.size < 6) continue

                val timeSec = parts[0].toDoubleOrNull() ?: continue
                val eastMeter = parts[1].toDoubleOrNull() ?: continue
                val northMeter = parts[2].toDoubleOrNull() ?: continue
                val latitudeDeg = parts[3].toDoubleOrNull() ?: continue
                val longitudeDeg = parts[4].toDoubleOrNull() ?: continue
                val accuracyMeter = parts[5].toFloatOrNull() ?: continue

                result.add(
                    GpsEnuSample(
                        relativeTimeSec = timeSec,
                        eastMeter = eastMeter,
                        northMeter = northMeter,
                        latitudeDeg = latitudeDeg,
                        longitudeDeg = longitudeDeg,
                        accuracyMeter = accuracyMeter
                    )
                )
            }
        }
        return result
    }
}
