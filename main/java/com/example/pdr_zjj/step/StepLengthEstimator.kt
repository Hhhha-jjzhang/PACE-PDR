package com.example.pdr_zjj.step

import kotlin.math.sqrt

class StepLengthEstimator(
    private val heightMeter:Double = 1.72,
    private val baseLengthFactor: Double = 0.33,
    private val cadenceFactor: Double = 0.08,
    private val amplitudeFactor: Double = 0.10,
    private val scaleFactor: Double = 1.0,
    private val minStepLengthMeter: Double = 0.35,
    private val maxStepLengthMeter: Double = 0.85
) {
    private val stepTimes = ArrayDeque<Double>()

    fun reset() {
        stepTimes.clear()
    }

    fun estimate(
        currentStepTimeSec: Double,
        stepAmplitude: Double
    ): Double {
        stepTimes.addLast(currentStepTimeSec)
        while (stepTimes.size > 3) {
            stepTimes.removeFirst()
        }

        val baseLength = (baseLengthFactor * heightMeter).coerceIn(0.40, 0.80)
        val amplitudeComp = amplitudeFactor * sqrt(sqrt(stepAmplitude.coerceAtLeast(0.05)))

        if (stepTimes.size < 3) {
            return ((baseLength + amplitudeComp) * scaleFactor)
                .coerceIn(minStepLengthMeter, maxStepLengthMeter)
        }

        val t0 = stepTimes.elementAt(0)
        val t1 = stepTimes.elementAt(1)
        val t2 = stepTimes.elementAt(2)

        val dt1 = t1 - t0
        val dt2 = t2 - t1
        val dtAvg = (dt1 + dt2) / 2.0

        if (dtAvg <= 1e-6) {
            return (baseLength + amplitudeComp).coerceIn(minStepLengthMeter, maxStepLengthMeter)
        }

        val stepFreq = 1.0 / dtAvg
        val cadenceComp = cadenceFactor * (stepFreq - 1.7)
        val stepLength = (baseLength + cadenceComp + amplitudeComp) * scaleFactor
        return stepLength.coerceIn(minStepLengthMeter, maxStepLengthMeter)
    }
}
