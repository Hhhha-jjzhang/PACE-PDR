package com.example.pdr_zjj.step

class StepDetector(
    private val baseThreshold: Double = 0.16,
    private val dynamicThresholdFactor: Double = 0.35,
    private val minPeakToValley: Double = 0.05,
    private val minStepIntervalSec: Double = 0.24,
    private val thresholdScale: Double = 1.0
) {
    private data class SamplePoint(
        val timeSec: Double,
        val value: Double
    )

    private val window = ArrayDeque<SamplePoint>()
    private val recentValues = ArrayDeque<Double>()
    private val accMagnitudeWindow = ArrayDeque<Double>()
    private val accZWindow = ArrayDeque<Double>()
    private val gyroMagnitudeWindow = ArrayDeque<Double>()
    private val recentSignalWindow = ArrayDeque<Double>()
    private var lastStepTimeSec: Double? = null
    private var stepCount = 0
    private var motionPrimed = false
    private var stationaryFrameCount = 0
    private var activeMotionFrameCount = 0

    fun reset() {
        window.clear()
        recentValues.clear()
        accMagnitudeWindow.clear()
        accZWindow.clear()
        gyroMagnitudeWindow.clear()
        recentSignalWindow.clear()
        lastStepTimeSec = null
        stepCount = 0
        motionPrimed = false
        stationaryFrameCount = 0
        activeMotionFrameCount = 0
    }

    fun update(
        timeSec: Double,
        verticalLinearAcc: Double,
        accMagnitude: Double,
        accZ: Double,
        gyroMagnitude: Double
    ): StepEvent? {
        accMagnitudeWindow.addLast(accMagnitude)
        while (accMagnitudeWindow.size > 25) {
            accMagnitudeWindow.removeFirst()
        }
        accZWindow.addLast(accZ)
        while (accZWindow.size > 25) {
            accZWindow.removeFirst()
        }
        gyroMagnitudeWindow.addLast(gyroMagnitude)
        while (gyroMagnitudeWindow.size > 25) {
            gyroMagnitudeWindow.removeFirst()
        }

        val accMagnitudeBaseline = if (accMagnitudeWindow.isEmpty()) {
            accMagnitude
        } else {
            accMagnitudeWindow.average()
        }
        val accZBaseline = if (accZWindow.isEmpty()) {
            accZ
        } else {
            accZWindow.average()
        }

        val magnitudeResidual = accMagnitude - accMagnitudeBaseline
        val zResidual = accZ - accZBaseline
        val fusedSignal =
            0.55 * kotlin.math.abs(verticalLinearAcc) +
            0.30 * kotlin.math.abs(magnitudeResidual) +
            0.35 * kotlin.math.abs(zResidual)

        window.addLast(SamplePoint(timeSec, fusedSignal))
        recentValues.addLast(fusedSignal)
        recentSignalWindow.addLast(fusedSignal)

        while (window.size > 5) {
            window.removeFirst()
        }
        while (recentValues.size > 40) {
            recentValues.removeFirst()
        }
        while (recentSignalWindow.size > 18) {
            recentSignalWindow.removeFirst()
        }

        if (window.size < 5) return null

        val p1 = window.elementAt(1)
        val p2 = window.elementAt(2)
        val p3 = window.elementAt(3)

        val adaptiveThreshold = computeAdaptiveThreshold()
        val recentGyroMean = gyroMagnitudeWindow.averageOrZero()
        val recentGyroPeak = gyroMagnitudeWindow.maxOrNull() ?: 0.0
        val recentSignalMean = recentSignalWindow.averageOrZero()
        val recentSignalPeak = recentSignalWindow.maxOrNull() ?: 0.0
        val recentSignalValley = recentSignalWindow.minOrNull() ?: 0.0
        val recentSignalRange = recentSignalPeak - recentSignalValley

        val isLikelyStationary =
            recentGyroMean < STATIONARY_GYRO_MEAN_RAD_PER_SEC &&
                recentGyroPeak < STATIONARY_GYRO_PEAK_RAD_PER_SEC &&
                recentSignalMean < STATIONARY_SIGNAL_MEAN_THRESHOLD &&
                recentSignalPeak < STATIONARY_SIGNAL_PEAK_THRESHOLD &&
                recentSignalRange < STATIONARY_SIGNAL_RANGE_THRESHOLD

        if (isLikelyStationary) {
            stationaryFrameCount += 1
            if (stationaryFrameCount >= STATIONARY_RELEASE_FRAMES) {
                motionPrimed = false
            }
            activeMotionFrameCount = 0
            return null
        }

        stationaryFrameCount = 0

        if (!motionPrimed) {
            val strongGyroMotion =
                recentGyroMean >= ACTIVATION_GYRO_MEAN_RAD_PER_SEC ||
                    recentGyroPeak >= ACTIVATION_GYRO_PEAK_RAD_PER_SEC
            val strongSignalMotion =
                recentSignalPeak >= ACTIVATION_SIGNAL_PEAK_THRESHOLD * effectiveThresholdScale() ||
                    recentSignalRange >= ACTIVATION_SIGNAL_RANGE_THRESHOLD * effectiveThresholdScale() ||
                    recentSignalMean >= ACTIVATION_SIGNAL_MEAN_THRESHOLD * effectiveThresholdScale()
            val motionActivated =
                (strongGyroMotion && recentSignalRange >= ACTIVATION_MIN_SIGNAL_RANGE_WITH_GYRO * effectiveThresholdScale()) ||
                    (strongSignalMotion && recentGyroPeak >= ACTIVATION_MIN_GYRO_PEAK_WITH_SIGNAL)

            if (!motionActivated) {
                activeMotionFrameCount = 0
                return null
            }

            activeMotionFrameCount += 1
            if (activeMotionFrameCount < ACTIVE_PRIME_FRAMES) return null

            motionPrimed = true
            activeMotionFrameCount = 0
        }

        val isPeak = p2.value > p1.value &&
            p2.value >= p3.value &&
            p2.value > adaptiveThreshold

        if (!isPeak) return null

        val lastTime = lastStepTimeSec
        if (lastTime != null && (p2.timeSec - lastTime) < minStepIntervalSec) {
            return null
        }

        val localValley = recentSignalValley
        val amplitude = p2.value - localValley
        if (amplitude < minPeakToValley * effectiveThresholdScale()) return null

        val peakMargin = p2.value - recentSignalMean
        if (peakMargin < MIN_PEAK_MARGIN * effectiveThresholdScale()) return null

        if (recentGyroPeak < STEP_GYRO_PEAK_THRESHOLD) return null
        if (recentSignalRange < STEP_SIGNAL_RANGE_THRESHOLD * effectiveThresholdScale()) return null

        lastStepTimeSec = p2.timeSec
        stepCount += 1

        return StepEvent(
            stepIndex = stepCount,
            timeSec = p2.timeSec,
            peakValue = p2.value,
            valleyValue = localValley,
            amplitude = amplitude
        )
    }

    fun getStepCount(): Int = stepCount

    private fun computeAdaptiveThreshold(): Double {
        if (recentValues.isEmpty()) return baseThreshold * effectiveThresholdScale()

        val mean = recentValues.average()
        var variance = 0.0
        for (value in recentValues) {
            val diff = value - mean
            variance += diff * diff
        }
        variance /= recentValues.size.toDouble()
        val std = kotlin.math.sqrt(variance)
        return maxOf(baseThreshold, mean + std * dynamicThresholdFactor) * effectiveThresholdScale()
    }

    private fun ArrayDeque<Double>.averageOrZero(): Double {
        return if (isEmpty()) 0.0 else average()
    }

    private fun effectiveThresholdScale(): Double {
        return thresholdScale.coerceIn(MIN_THRESHOLD_SCALE, MAX_THRESHOLD_SCALE)
    }

    private companion object {
        private const val MIN_THRESHOLD_SCALE = 0.70
        private const val MAX_THRESHOLD_SCALE = 2.00
        private const val STATIONARY_GYRO_MEAN_RAD_PER_SEC = 0.08
        private const val STATIONARY_GYRO_PEAK_RAD_PER_SEC = 0.18
        private const val STATIONARY_SIGNAL_MEAN_THRESHOLD = 0.12
        private const val STATIONARY_SIGNAL_PEAK_THRESHOLD = 0.22
        private const val STATIONARY_SIGNAL_RANGE_THRESHOLD = 0.08
        private const val STATIONARY_RELEASE_FRAMES = 12
        private const val ACTIVATION_GYRO_MEAN_RAD_PER_SEC = 0.12
        private const val ACTIVATION_GYRO_PEAK_RAD_PER_SEC = 0.25
        private const val ACTIVATION_SIGNAL_MEAN_THRESHOLD = 0.15
        private const val ACTIVATION_SIGNAL_PEAK_THRESHOLD = 0.32
        private const val ACTIVATION_SIGNAL_RANGE_THRESHOLD = 0.14
        private const val ACTIVATION_MIN_SIGNAL_RANGE_WITH_GYRO = 0.10
        private const val ACTIVATION_MIN_GYRO_PEAK_WITH_SIGNAL = 0.12
        private const val ACTIVE_PRIME_FRAMES = 6
        private const val MIN_PEAK_MARGIN = 0.08
        private const val STEP_GYRO_PEAK_THRESHOLD = 0.20
        private const val STEP_SIGNAL_RANGE_THRESHOLD = 0.10
    }
}
