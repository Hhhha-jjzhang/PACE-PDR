package com.example.pdr_zjj.data.storage

import java.io.File
import java.io.BufferedWriter
import java.io.FileWriter
import java.util.Locale

class PdrResultWriter(
    private val file: File
) {
    private var writer: BufferedWriter? = null

    fun open(clearIfExists: Boolean = false) {
        if (clearIfExists && file.exists()) {
            file.delete()
        }

        writer = BufferedWriter(FileWriter(file, true))

        if (file.length() == 0L) {
            writer?.write("step_index,time_sec,step_length_m,heading_deg,east_m,north_m\n")
            writer?.flush()
        }
    }

    fun writeStep(
        stepIndex: Int,
        timeSec: Double,
        stepLengthMeter: Double,
        headingDeg: Double,
        eastMeter: Double,
        northMeter: Double
    ) {
        val line = String.format(
            Locale.US,
            "%d,%.3f,%.4f,%.2f,%.4f,%.4f\n",
            stepIndex,
            timeSec,
            stepLengthMeter,
            headingDeg,
            eastMeter,
            northMeter
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
    }
}