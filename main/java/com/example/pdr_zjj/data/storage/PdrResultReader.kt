package com.example.pdr_zjj.data.storage

import com.example.pdr_zjj.data.model.PdrTrackPoint
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class PdrResultReader {

    fun read(file: File): List<PdrTrackPoint> {
        if (!file.exists()) return emptyList()

        val result = mutableListOf<PdrTrackPoint>()
        BufferedReader(FileReader(file)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val row = line.trim()
                if (row.isEmpty()) continue
                if (row.startsWith("step_index", ignoreCase = true)) continue

                val parts = row.split(",")
                if (parts.size < 6) continue

                val stepIndex = parts[0].toIntOrNull() ?: continue
                val timeSec = parts[1].toDoubleOrNull() ?: continue
                val eastMeter = parts[4].toDoubleOrNull() ?: continue
                val northMeter = parts[5].toDoubleOrNull() ?: continue

                result.add(
                    PdrTrackPoint(
                        stepIndex = stepIndex,
                        timeSec = timeSec,
                        eastMeter = eastMeter,
                        northMeter = northMeter
                    )
                )
            }
        }
        return result
    }
}
