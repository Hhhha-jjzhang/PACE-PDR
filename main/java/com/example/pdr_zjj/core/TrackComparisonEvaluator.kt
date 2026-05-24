package com.example.pdr_zjj.core

import com.example.pdr_zjj.data.model.GpsEnuSample
import com.example.pdr_zjj.data.model.PdrTrackPoint
import kotlin.math.hypot

class TrackComparisonEvaluator {

    fun evaluate(
        pdrPoints: List<PdrTrackPoint>,
        gpsPoints: List<GpsEnuSample>
    ): TrackComparisonMetrics? {
        if (pdrPoints.isEmpty() || gpsPoints.size < 2) return null

        val alignedErrors = pdrPoints.mapNotNull { pdr ->
            val gps = interpolateGpsAtTime(gpsPoints, pdr.timeSec) ?: return@mapNotNull null
            distance(
                pdr.eastMeter,
                pdr.northMeter,
                gps.eastMeter,
                gps.northMeter
            )
        }

        if (alignedErrors.isEmpty()) return null

        val pdrPathLength = computePathLength(
            pdrPoints.map { it.eastMeter to it.northMeter }
        )
        val gpsPathLength = computePathLength(
            gpsPoints.map { it.eastMeter to it.northMeter }
        )

        val pdrEnd = pdrPoints.last()
        val gpsEndAligned = interpolateGpsAtTime(gpsPoints, pdrEnd.timeSec) ?: return null

        return TrackComparisonMetrics(
            pdrPointCount = pdrPoints.size,
            gpsPointCount = gpsPoints.size,
            pdrPathLengthMeter = pdrPathLength,
            gpsPathLengthMeter = gpsPathLength,
            endpointErrorMeter = distance(
                pdrEnd.eastMeter, pdrEnd.northMeter,
                gpsEndAligned.eastMeter, gpsEndAligned.northMeter
            ),
            meanTrackErrorMeter = alignedErrors.average(),
            maxTrackErrorMeter = alignedErrors.maxOrNull() ?: 0.0,
            pdrClosureErrorMeter = distance(
                pdrPoints.first().eastMeter, pdrPoints.first().northMeter,
                pdrEnd.eastMeter, pdrEnd.northMeter
            ),
            gpsClosureErrorMeter = distance(
                gpsPoints.first().eastMeter, gpsPoints.first().northMeter,
                gpsPoints.last().eastMeter, gpsPoints.last().northMeter
            )
        )
    }

    private fun interpolateGpsAtTime(
        gpsPoints: List<GpsEnuSample>,
        targetTimeSec: Double
    ): GpsEnuSample? {
        if (gpsPoints.isEmpty()) return null
        if (targetTimeSec < gpsPoints.first().relativeTimeSec) return null
        if (targetTimeSec > gpsPoints.last().relativeTimeSec) return null
        if (targetTimeSec == gpsPoints.first().relativeTimeSec) return gpsPoints.first()
        if (targetTimeSec == gpsPoints.last().relativeTimeSec) return gpsPoints.last()

        for (i in 1 until gpsPoints.size) {
            val prev = gpsPoints[i - 1]
            val next = gpsPoints[i]
            if (targetTimeSec > next.relativeTimeSec) continue

            val dt = next.relativeTimeSec - prev.relativeTimeSec
            if (dt <= 1e-9) return prev

            val ratio = ((targetTimeSec - prev.relativeTimeSec) / dt).coerceIn(0.0, 1.0)
            return GpsEnuSample(
                relativeTimeSec = targetTimeSec,
                eastMeter = prev.eastMeter + (next.eastMeter - prev.eastMeter) * ratio,
                northMeter = prev.northMeter + (next.northMeter - prev.northMeter) * ratio,
                latitudeDeg = prev.latitudeDeg + (next.latitudeDeg - prev.latitudeDeg) * ratio,
                longitudeDeg = prev.longitudeDeg + (next.longitudeDeg - prev.longitudeDeg) * ratio,
                accuracyMeter = prev.accuracyMeter + (next.accuracyMeter - prev.accuracyMeter) * ratio.toFloat()
            )
        }

        return gpsPoints.last()
    }

    private fun computePathLength(points: List<Pair<Double, Double>>): Double {
        var length = 0.0
        for (i in 1 until points.size) {
            length += distance(
                points[i - 1].first,
                points[i - 1].second,
                points[i].first,
                points[i].second
            )
        }
        return length
    }

    private fun distance(e1: Double, n1: Double, e2: Double, n2: Double): Double {
        return hypot(e2 - e1, n2 - n1)
    }
}
