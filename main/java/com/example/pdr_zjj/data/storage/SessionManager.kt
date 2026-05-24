package com.example.pdr_zjj.data.storage

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionManager(private val context: Context) {

    fun createSessionDir(customName: String? = null): File {
        val timeText = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sessionName = if (!customName.isNullOrBlank()) {
            "${customName}_$timeText"
        } else {
            "session_$timeText"
        }

        val dir = File(context.filesDir, sessionName)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createRawSensorFile(sessionDir: File): File {
        return File(sessionDir, "raw_sensor.txt")
    }

    fun createGpsTruthFile(sessionDir: File): File {
        return File(sessionDir, "gps_truth.csv")
    }

    fun createGpsTruthEnuFile(sessionDir: File): File {
        return File(sessionDir, "gps_truth_enu.csv")
    }

    fun createTrackComparisonFile(sessionDir: File): File {
        return File(sessionDir, "track_comparison_summary.txt")
    }

    fun createTrackImageFile(sessionDir: File): File {
        return File(sessionDir, "track.png")
    }

    fun createRealtimePdrResultFile(sessionDir: File): File {
        return File(sessionDir, "pdr_result.csv")
    }

    fun createOfflinePdrResultFile(sessionDir: File): File {
        return File(sessionDir, "pdr_offline_result.csv")
    }
}
