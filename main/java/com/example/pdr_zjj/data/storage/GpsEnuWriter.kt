package com.example.pdr_zjj.data.storage

import com.example.pdr_zjj.data.model.GpsEnuSample
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Locale

class GpsEnuWriter(
    private val file: File
) {
    private var writer: BufferedWriter? = null
    private var isOpened = false

    fun open(clearIfExists: Boolean = true) {
        if (clearIfExists && file.exists()) {
            file.writeText("")
        }
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }

        writer = BufferedWriter(FileWriter(file, true))
        writer?.write("time_sec,east_m,north_m,latitude_deg,longitude_deg,accuracy_m\n")
        writer?.flush()
        isOpened = true
    }

    fun write(sample: GpsEnuSample) {
        if (!isOpened) return

        val line = String.format(
            Locale.US,
            "%.3f,%.3f,%.3f,%.9f,%.9f,%.3f\n",
            sample.relativeTimeSec,
            sample.eastMeter,
            sample.northMeter,
            sample.latitudeDeg,
            sample.longitudeDeg,
            sample.accuracyMeter
        )
        writer?.write(line)
    }

    fun flush() {
        writer?.flush()
    }

    fun close() {
        writer?.flush()
        writer?.close()
        writer = null
        isOpened = false
    }
}
