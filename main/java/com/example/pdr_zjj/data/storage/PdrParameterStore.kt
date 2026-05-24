package com.example.pdr_zjj.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.pdr_zjj.ahrs.AhrsMode
import com.example.pdr_zjj.core.PdrConfig
import com.example.pdr_zjj.mode.PdrMode

class PdrParameterStore(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHeightMeter(): Double = getDouble(KEY_HEIGHT_METER, DEFAULT_HEIGHT_METER)

    fun getDeclinationDeg(): Double = getDouble(KEY_DECLINATION_DEG, DEFAULT_DECLINATION_DEG)

    fun getRealtimeParameters(ahrsMode: AhrsMode): StepLengthParameters {
        return if (ahrsMode == AhrsMode.DOF6) {
            StepLengthParameters(
                baseLengthFactor = getDouble(KEY_REALTIME_6_BASE, DEFAULT_REALTIME_6.baseLengthFactor),
                cadenceFactor = getDouble(KEY_REALTIME_6_CADENCE, DEFAULT_REALTIME_6.cadenceFactor),
                amplitudeFactor = getDouble(KEY_REALTIME_6_AMPLITUDE, DEFAULT_REALTIME_6.amplitudeFactor),
                scaleFactor = getDouble(KEY_REALTIME_6_SCALE, DEFAULT_REALTIME_6.scaleFactor),
                minStepIntervalSec = getDouble(
                    KEY_REALTIME_6_MIN_STEP_INTERVAL,
                    DEFAULT_REALTIME_6.minStepIntervalSec
                ),
                detectionThresholdScale = getDouble(
                    KEY_REALTIME_6_DETECTION_THRESHOLD,
                    DEFAULT_REALTIME_6.detectionThresholdScale
                )
            )
        } else {
            StepLengthParameters(
                baseLengthFactor = getDouble(KEY_REALTIME_9_BASE, DEFAULT_REALTIME_9.baseLengthFactor),
                cadenceFactor = getDouble(KEY_REALTIME_9_CADENCE, DEFAULT_REALTIME_9.cadenceFactor),
                amplitudeFactor = getDouble(KEY_REALTIME_9_AMPLITUDE, DEFAULT_REALTIME_9.amplitudeFactor),
                scaleFactor = getDouble(KEY_REALTIME_9_SCALE, DEFAULT_REALTIME_9.scaleFactor),
                minStepIntervalSec = getDouble(
                    KEY_REALTIME_9_MIN_STEP_INTERVAL,
                    DEFAULT_REALTIME_9.minStepIntervalSec
                ),
                detectionThresholdScale = getDouble(
                    KEY_REALTIME_9_DETECTION_THRESHOLD,
                    DEFAULT_REALTIME_9.detectionThresholdScale
                )
            )
        }
    }

    fun getOfflineParameters(ahrsMode: AhrsMode): StepLengthParameters {
        return if (ahrsMode == AhrsMode.DOF6) {
            StepLengthParameters(
                baseLengthFactor = getDouble(KEY_OFFLINE_6_BASE, DEFAULT_OFFLINE_6.baseLengthFactor),
                cadenceFactor = getDouble(KEY_OFFLINE_6_CADENCE, DEFAULT_OFFLINE_6.cadenceFactor),
                amplitudeFactor = getDouble(KEY_OFFLINE_6_AMPLITUDE, DEFAULT_OFFLINE_6.amplitudeFactor),
                scaleFactor = getDouble(KEY_OFFLINE_6_SCALE, DEFAULT_OFFLINE_6.scaleFactor),
                minStepIntervalSec = getDouble(
                    KEY_OFFLINE_6_MIN_STEP_INTERVAL,
                    DEFAULT_OFFLINE_6.minStepIntervalSec
                ),
                detectionThresholdScale = getDouble(
                    KEY_OFFLINE_6_DETECTION_THRESHOLD,
                    DEFAULT_OFFLINE_6.detectionThresholdScale
                )
            )
        } else {
            StepLengthParameters(
                baseLengthFactor = getDouble(KEY_OFFLINE_9_BASE, DEFAULT_OFFLINE_9.baseLengthFactor),
                cadenceFactor = getDouble(KEY_OFFLINE_9_CADENCE, DEFAULT_OFFLINE_9.cadenceFactor),
                amplitudeFactor = getDouble(KEY_OFFLINE_9_AMPLITUDE, DEFAULT_OFFLINE_9.amplitudeFactor),
                scaleFactor = getDouble(KEY_OFFLINE_9_SCALE, DEFAULT_OFFLINE_9.scaleFactor),
                minStepIntervalSec = getDouble(
                    KEY_OFFLINE_9_MIN_STEP_INTERVAL,
                    DEFAULT_OFFLINE_9.minStepIntervalSec
                ),
                detectionThresholdScale = getDouble(
                    KEY_OFFLINE_9_DETECTION_THRESHOLD,
                    DEFAULT_OFFLINE_9.detectionThresholdScale
                )
            )
        }
    }

    fun buildRealtimeConfig(ahrsMode: AhrsMode): PdrConfig {
        val is6Dof = ahrsMode == AhrsMode.DOF6
        val parameters = getRealtimeParameters(ahrsMode)
        return PdrConfig(
            mode = PdrMode.REALTIME,
            ahrsMode = ahrsMode,
            accSmoothWindow = 5,
            gyroSmoothWindow = 5,
            magSmoothWindow = 7,
            initAccSampleCount = 120,
            initMagSampleCount = 80,
            stepBaseThreshold = if (is6Dof) 0.20 else 0.22,
            stepDynamicThresholdFactor = if (is6Dof) 0.42 else 0.46,
            stepMinPeakToValley = if (is6Dof) 0.08 else 0.09,
            minStepIntervalSec = parameters.minStepIntervalSec,
            stepDetectionThresholdScale = parameters.detectionThresholdScale,
            stepBaseLengthFactor = parameters.baseLengthFactor,
            stepCadenceFactor = parameters.cadenceFactor,
            stepAmplitudeFactor = parameters.amplitudeFactor,
            stepLengthScaleFactor = parameters.scaleFactor,
            minStepLengthMeter = if (is6Dof) 0.30 else 0.32,
            maxStepLengthMeter = if (is6Dof) 0.78 else 0.88,
            interruptThresholdSec = 1.5,
            headingCorrectionBlendFactor = 0.55,
            heightMeter = getHeightMeter(),
            declinationDeg = getDeclinationDeg(),
            useGpsPosition = false
        )
    }

    fun buildOfflineConfig(ahrsMode: AhrsMode): PdrConfig {
        val is6Dof = ahrsMode == AhrsMode.DOF6
        val parameters = getOfflineParameters(ahrsMode)
        return PdrConfig(
            mode = PdrMode.OFFLINE,
            ahrsMode = ahrsMode,
            accSmoothWindow = 5,
            gyroSmoothWindow = 7,
            magSmoothWindow = 9,
            initAccSampleCount = 150,
            initMagSampleCount = 100,
            stepBaseThreshold = if (is6Dof) 0.18 else 0.18,
            stepDynamicThresholdFactor = if (is6Dof) 0.38 else 0.40,
            stepMinPeakToValley = if (is6Dof) 0.07 else 0.07,
            minStepIntervalSec = parameters.minStepIntervalSec,
            stepDetectionThresholdScale = parameters.detectionThresholdScale,
            stepBaseLengthFactor = parameters.baseLengthFactor,
            stepCadenceFactor = parameters.cadenceFactor,
            stepAmplitudeFactor = parameters.amplitudeFactor,
            stepLengthScaleFactor = parameters.scaleFactor,
            minStepLengthMeter = if (is6Dof) 0.28 else 0.35,
            maxStepLengthMeter = if (is6Dof) 0.72 else 1.02,
            interruptThresholdSec = 1.5,
            headingCorrectionBlendFactor = 0.50,
            heightMeter = getHeightMeter(),
            declinationDeg = getDeclinationDeg(),
            useGpsPosition = false
        )
    }

    fun saveGlobalParameters(heightMeter: Double, declinationDeg: Double) {
        preferences.edit()
            .putString(KEY_HEIGHT_METER, heightMeter.toString())
            .putString(KEY_DECLINATION_DEG, declinationDeg.toString())
            .apply()
    }

    fun saveRealtimeParameters(ahrsMode: AhrsMode, parameters: StepLengthParameters) {
        saveStepLengthParameters(
            if (ahrsMode == AhrsMode.DOF6) KEY_REALTIME_6_BASE else KEY_REALTIME_9_BASE,
            if (ahrsMode == AhrsMode.DOF6) KEY_REALTIME_6_CADENCE else KEY_REALTIME_9_CADENCE,
            if (ahrsMode == AhrsMode.DOF6) KEY_REALTIME_6_AMPLITUDE else KEY_REALTIME_9_AMPLITUDE,
            if (ahrsMode == AhrsMode.DOF6) KEY_REALTIME_6_SCALE else KEY_REALTIME_9_SCALE,
            if (ahrsMode == AhrsMode.DOF6) {
                KEY_REALTIME_6_MIN_STEP_INTERVAL
            } else {
                KEY_REALTIME_9_MIN_STEP_INTERVAL
            },
            if (ahrsMode == AhrsMode.DOF6) {
                KEY_REALTIME_6_DETECTION_THRESHOLD
            } else {
                KEY_REALTIME_9_DETECTION_THRESHOLD
            },
            parameters
        )
    }

    fun saveOfflineParameters(ahrsMode: AhrsMode, parameters: StepLengthParameters) {
        saveStepLengthParameters(
            if (ahrsMode == AhrsMode.DOF6) KEY_OFFLINE_6_BASE else KEY_OFFLINE_9_BASE,
            if (ahrsMode == AhrsMode.DOF6) KEY_OFFLINE_6_CADENCE else KEY_OFFLINE_9_CADENCE,
            if (ahrsMode == AhrsMode.DOF6) KEY_OFFLINE_6_AMPLITUDE else KEY_OFFLINE_9_AMPLITUDE,
            if (ahrsMode == AhrsMode.DOF6) KEY_OFFLINE_6_SCALE else KEY_OFFLINE_9_SCALE,
            if (ahrsMode == AhrsMode.DOF6) {
                KEY_OFFLINE_6_MIN_STEP_INTERVAL
            } else {
                KEY_OFFLINE_9_MIN_STEP_INTERVAL
            },
            if (ahrsMode == AhrsMode.DOF6) {
                KEY_OFFLINE_6_DETECTION_THRESHOLD
            } else {
                KEY_OFFLINE_9_DETECTION_THRESHOLD
            },
            parameters
        )
    }

    fun resetToDefaults() {
        preferences.edit().clear().apply()
    }

    private fun saveStepLengthParameters(
        baseKey: String,
        cadenceKey: String,
        amplitudeKey: String,
        scaleKey: String,
        minStepIntervalKey: String,
        detectionThresholdKey: String,
        parameters: StepLengthParameters
    ) {
        preferences.edit()
            .putString(baseKey, parameters.baseLengthFactor.toString())
            .putString(cadenceKey, parameters.cadenceFactor.toString())
            .putString(amplitudeKey, parameters.amplitudeFactor.toString())
            .putString(scaleKey, parameters.scaleFactor.toString())
            .putString(minStepIntervalKey, parameters.minStepIntervalSec.toString())
            .putString(detectionThresholdKey, parameters.detectionThresholdScale.toString())
            .apply()
    }

    private fun getDouble(key: String, defaultValue: Double): Double {
        return preferences.getString(key, null)?.toDoubleOrNull() ?: defaultValue
    }

    data class StepLengthParameters(
        val baseLengthFactor: Double,
        val cadenceFactor: Double,
        val amplitudeFactor: Double,
        val scaleFactor: Double,
        val minStepIntervalSec: Double,
        val detectionThresholdScale: Double
    )

    companion object {
        private const val PREFS_NAME = "pdr_parameters"

        private const val KEY_HEIGHT_METER = "height_meter"
        private const val KEY_DECLINATION_DEG = "declination_deg"

        private const val KEY_REALTIME_6_BASE = "realtime_6_base"
        private const val KEY_REALTIME_6_CADENCE = "realtime_6_cadence"
        private const val KEY_REALTIME_6_AMPLITUDE = "realtime_6_amplitude"
        private const val KEY_REALTIME_6_SCALE = "realtime_6_scale"
        private const val KEY_REALTIME_6_MIN_STEP_INTERVAL = "realtime_6_min_step_interval"
        private const val KEY_REALTIME_6_DETECTION_THRESHOLD = "realtime_6_detection_threshold"

        private const val KEY_REALTIME_9_BASE = "realtime_9_base"
        private const val KEY_REALTIME_9_CADENCE = "realtime_9_cadence"
        private const val KEY_REALTIME_9_AMPLITUDE = "realtime_9_amplitude"
        private const val KEY_REALTIME_9_SCALE = "realtime_9_scale"
        private const val KEY_REALTIME_9_MIN_STEP_INTERVAL = "realtime_9_min_step_interval"
        private const val KEY_REALTIME_9_DETECTION_THRESHOLD = "realtime_9_detection_threshold"

        private const val KEY_OFFLINE_6_BASE = "offline_6_base"
        private const val KEY_OFFLINE_6_CADENCE = "offline_6_cadence"
        private const val KEY_OFFLINE_6_AMPLITUDE = "offline_6_amplitude"
        private const val KEY_OFFLINE_6_SCALE = "offline_6_scale"
        private const val KEY_OFFLINE_6_MIN_STEP_INTERVAL = "offline_6_min_step_interval"
        private const val KEY_OFFLINE_6_DETECTION_THRESHOLD = "offline_6_detection_threshold"

        private const val KEY_OFFLINE_9_BASE = "offline_9_base"
        private const val KEY_OFFLINE_9_CADENCE = "offline_9_cadence"
        private const val KEY_OFFLINE_9_AMPLITUDE = "offline_9_amplitude"
        private const val KEY_OFFLINE_9_SCALE = "offline_9_scale"
        private const val KEY_OFFLINE_9_MIN_STEP_INTERVAL = "offline_9_min_step_interval"
        private const val KEY_OFFLINE_9_DETECTION_THRESHOLD = "offline_9_detection_threshold"

        const val DEFAULT_HEIGHT_METER = 1.72
        const val DEFAULT_DECLINATION_DEG = 0.0

        val DEFAULT_REALTIME_6 = StepLengthParameters(
            baseLengthFactor = 0.31,
            cadenceFactor = 0.08,
            amplitudeFactor = 0.12,
            scaleFactor = 0.68,
            minStepIntervalSec = 0.32,
            detectionThresholdScale = 1.00
        )
        val DEFAULT_REALTIME_9 = StepLengthParameters(
            baseLengthFactor = 0.32,
            cadenceFactor = 0.08,
            amplitudeFactor = 0.10,
            scaleFactor = 0.58,
            minStepIntervalSec = 0.40,
            detectionThresholdScale = 1.30
        )
        val DEFAULT_OFFLINE_6 = StepLengthParameters(
            baseLengthFactor = 0.30,
            cadenceFactor = 0.07,
            amplitudeFactor = 0.10,
            scaleFactor = 0.72,
            minStepIntervalSec = 0.30,
            detectionThresholdScale = 1.00
        )
        val DEFAULT_OFFLINE_9 = StepLengthParameters(
            baseLengthFactor = 0.39,
            cadenceFactor = 0.08,
            amplitudeFactor = 0.10,
            scaleFactor = 0.96,
            minStepIntervalSec = 0.31,
            detectionThresholdScale = 1.26
        )
    }
}
