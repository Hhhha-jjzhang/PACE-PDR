package com.example.pdr_zjj.data.storage

import com.example.pdr_zjj.data.model.SensorSample
import java.io.File
import java.io.BufferedWriter
import java.io.FileWriter
import java.util.Locale

class RawDataWriter(
    private val outputFile: File
) {
    private var writer: BufferedWriter? = null
    private var isOpened = false

    fun open(clearIfExists: Boolean = true) {
        if (clearIfExists && outputFile.exists()) {
            outputFile.writeText("")
        }
        if (!outputFile.exists()) {
            outputFile.parentFile?.mkdirs()
            outputFile.createNewFile()
        }

        writer = BufferedWriter(FileWriter(outputFile, true))
        writer?.write("type,time,x,y,z,sensorTimestampNs,systemTimeMillis\n")
        writer?.flush()
        isOpened = true
    }

    fun write(sample: SensorSample) {
        if (!isOpened) return

        val line = String.format(
            Locale.US,
            "%d,%.6f,%.9f,%.9f,%.9f,%d,%d\n",
            sample.sensorType.code,
            sample.relativeTimeSec,
            sample.x,
            sample.y,
            sample.z,
            sample.sensorTimestampNs,
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