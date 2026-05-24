package com.example.pdr_zjj.data.storage

import com.example.pdr_zjj.core.TrackComparisonMetrics
import java.io.File
import java.util.Locale

class TrackComparisonWriter {

    fun writeSummary(file: File, metrics: TrackComparisonMetrics) {
        val text = buildString {
            appendLine("pdr_point_count=${metrics.pdrPointCount}")
            appendLine("gps_point_count=${metrics.gpsPointCount}")
            appendLine("pdr_path_length_m=${format(metrics.pdrPathLengthMeter)}")
            appendLine("gps_path_length_m=${format(metrics.gpsPathLengthMeter)}")
            appendLine("endpoint_error_m=${format(metrics.endpointErrorMeter)}")
            appendLine("mean_track_error_m=${format(metrics.meanTrackErrorMeter)}")
            appendLine("max_track_error_m=${format(metrics.maxTrackErrorMeter)}")
            appendLine("pdr_closure_error_m=${format(metrics.pdrClosureErrorMeter)}")
            appendLine("gps_closure_error_m=${format(metrics.gpsClosureErrorMeter)}")
        }
        file.writeText(text)
    }

    private fun format(value: Double): String {
        return String.format(Locale.US, "%.4f", value)
    }
}
