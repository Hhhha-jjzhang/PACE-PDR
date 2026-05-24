package com.example.pdr_zjj.data.storage

import com.example.pdr_zjj.data.model.GpsSample
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Locale

class GpsTruthWriter(
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
        writer?.write(
            "provider,time_sec,latitude_deg,longitude_deg,altitude_m,accuracy_m,speed_mps,bearing_deg,location_time_millis,system_time_millis\n"
        )
        writer?.flush()
        isOpened = true
    }

    fun write(sample: GpsSample) {
        if (!isOpened) return

        val line = String.format(
            Locale.US,
            "%s,%.3f,%.9f,%.9f,%s,%.3f,%s,%s,%d,%d\n",
            sample.provider,
            sample.relativeTimeSec,
            sample.latitudeDeg,
            sample.longitudeDeg,
            sample.altitudeMeter?.let { String.format(Locale.US, "%.3f", it) } ?: "",
            sample.accuracyMeter,
            sample.speedMeterPerSec?.let { String.format(Locale.US, "%.3f", it) } ?: "",
            sample.bearingDeg?.let { String.format(Locale.US, "%.3f", it) } ?: "",
            sample.locationTimeMillis,
            sample.systemTimeMillis
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
