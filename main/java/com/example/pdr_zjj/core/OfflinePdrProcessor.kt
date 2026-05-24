package com.example.pdr_zjj.core

import android.content.Context
import com.example.pdr_zjj.ahrs.Ahrs6DofMahony
import com.example.pdr_zjj.ahrs.Ahrs9DofMahony
import com.example.pdr_zjj.ahrs.IAhrsFilter
import com.example.pdr_zjj.ahrs.QuaternionUtils
import com.example.pdr_zjj.data.model.SensorType
import com.example.pdr_zjj.data.storage.CompatibleRawDataReader
import com.example.pdr_zjj.init.InitManager
import com.example.pdr_zjj.mode.CarryMode
import com.example.pdr_zjj.position.HeadingCorrector
import com.example.pdr_zjj.position.PocketHeadingTuner
import com.example.pdr_zjj.position.PositionUpdater
import com.example.pdr_zjj.position.WalkingDirectionEstimator
import com.example.pdr_zjj.preprocess.core.PreprocessEngine
import com.example.pdr_zjj.step.InterruptDetector
import com.example.pdr_zjj.step.StepDetector
import com.example.pdr_zjj.step.StepLengthEstimator
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
class OfflinePdrProcessor(
    context: Context,
    private val config: PdrConfig,
    private val carryMode: CarryMode = CarryMode.HANDHELD
) {
    private val rawDataReader = CompatibleRawDataReader()

    private val preprocessEngine = PreprocessEngine(
        accSmoothWindow = config.accSmoothWindow,
        gyroSmoothWindow = config.gyroSmoothWindow,
        magSmoothWindow = config.magSmoothWindow
    )

    private val initManager = InitManager(
        context = context,
        declinationDeg = config.declinationDeg
    )

    private val ahrsFilter: IAhrsFilter = createAhrsFilter()

    private val stepDetector = StepDetector(
        baseThreshold = config.stepBaseThreshold,
        dynamicThresholdFactor = config.stepDynamicThresholdFactor,
        minPeakToValley = config.stepMinPeakToValley,
        minStepIntervalSec = config.minStepIntervalSec,
        thresholdScale = config.stepDetectionThresholdScale
    )

    private val stepLengthEstimator = StepLengthEstimator(
        heightMeter = config.heightMeter,
        baseLengthFactor = config.stepBaseLengthFactor,
        cadenceFactor = config.stepCadenceFactor,
        amplitudeFactor = config.stepAmplitudeFactor,
        scaleFactor = config.stepLengthScaleFactor,
        minStepLengthMeter = config.minStepLengthMeter,
        maxStepLengthMeter = config.maxStepLengthMeter
    )

    private val positionUpdater = PositionUpdater()

    private val interruptDetector = InterruptDetector(
        interruptThresholdSec = config.interruptThresholdSec
    )

    private val headingCorrector = HeadingCorrector(
        declinationDeg = config.declinationDeg
    )
    private var stabilizationEndTimeSec: Double? = null
    private var startupSuppressionEndTimeSec: Double? = null
    private var startupDiscardedStepCount = 0
    private var initialPositionSuppressedStepCount = 0
    private var emittedPositionStepCount = 0
    private val startupHeadingSamples = ArrayDeque<Double>()
    private var startupHeadingReferenceRad: Double? = null
    private val yawHistory = ArrayDeque<Double>()
    private val magneticYawHistory = ArrayDeque<Double>()
    private val walkingDirectionEstimator = WalkingDirectionEstimator()

    fun process(file: File): OfflinePdrResult {
        val samples = rawDataReader.read(file)
        if (samples.isEmpty()) {
            return OfflinePdrResult(emptyList())
        }

        preprocessEngine.reset()
        ahrsFilter.reset()
        stepDetector.reset()
        stepLengthEstimator.reset()
        positionUpdater.reset()
        interruptDetector.reset()
        yawHistory.clear()
        magneticYawHistory.clear()
        walkingDirectionEstimator.reset()
        stabilizationEndTimeSec = null
        startupSuppressionEndTimeSec = null
        startupDiscardedStepCount = 0
        initialPositionSuppressedStepCount = 0
        emittedPositionStepCount = 0
        startupHeadingSamples.clear()
        startupHeadingReferenceRad = null

        val accInitBuffer = mutableListOf<FloatArray>()
        val magInitBuffer = mutableListOf<FloatArray>()

        var isInitialized = false
        var latestYawRad = 0.0
        var lastStepYawRad: Double? = null

        val stepResults = mutableListOf<OfflineStepResult>()

        for (sample in samples) {
            if (!isInitialized) {
                when (sample.sensorType) {
                    SensorType.ACCELEROMETER -> {
                        accInitBuffer.add(
                            floatArrayOf(
                                sample.x.toFloat(),
                                sample.y.toFloat(),
                                sample.z.toFloat()
                            )
                        )
                    }

                    SensorType.MAGNETOMETER -> {
                        magInitBuffer.add(
                            floatArrayOf(
                                sample.x.toFloat(),
                                sample.y.toFloat(),
                                sample.z.toFloat()
                            )
                        )
                    }

                    SensorType.GYROSCOPE -> Unit
                }

                val accEnough = accInitBuffer.size >= config.initAccSampleCount
                val magEnough = magInitBuffer.size >= config.initMagSampleCount

                if (accEnough && magEnough) {
                    val initResult = initManager.initializeAll(
                        accSamples = accInitBuffer,
                        magSamples = magInitBuffer,
                        useGpsPosition = config.useGpsPosition
                    )

                    if (initResult.success) {
                        val rollRad = initResult.attitude.rollRad
                        val pitchRad = initResult.attitude.pitchRad
                        val headingRad = initResult.attitude.headingTrueRad

                        val q0 = QuaternionUtils.fromEuler(
                            rollRad,
                            pitchRad,
                            QuaternionUtils.compassHeadingToYawRad(
                                QuaternionUtils.displayHeadingToInternalRad(headingRad)
                            )
                        )
                        ahrsFilter.setInitialQuaternion(q0)

                        latestYawRad = headingRad
                        isInitialized = true
                        stabilizationEndTimeSec = sample.relativeTimeSec + INITIAL_STABILIZATION_SEC
                        startupSuppressionEndTimeSec =
                            (stabilizationEndTimeSec ?: sample.relativeTimeSec) + startupStepSuppressionSec()
                    }
                }
            }

            val frame = preprocessEngine.accept(sample) ?: continue
            if (!isInitialized) continue

            val pose = ahrsFilter.update(frame) ?: continue
            walkingDirectionEstimator.addFrame(
                quaternion = ahrsFilter.getQuaternion(),
                ax = frame.accX,
                ay = frame.accY,
                az = frame.accZ,
                rollRad = pose.rollRad,
                pitchRad = pose.pitchRad
            )
            latestYawRad = pose.yawRad

            val settleEnd = stabilizationEndTimeSec
            if (settleEnd != null && frame.relativeTimeSec < settleEnd) {
                continue
            }

            val verticalLinearAcc = QuaternionUtils.verticalLinearAcceleration(
                q = ahrsFilter.getQuaternion(),
                ax = frame.accX,
                ay = frame.accY,
                az = frame.accZ
            )
            val accMagnitude = sqrt(
                frame.accX * frame.accX +
                    frame.accY * frame.accY +
                    frame.accZ * frame.accZ
            )
            val gyroMagnitude = sqrt(
                frame.gyroX * frame.gyroX +
                    frame.gyroY * frame.gyroY +
                    frame.gyroZ * frame.gyroZ
            )

            val stableMagneticYaw = if (config.ahrsMode == com.example.pdr_zjj.ahrs.AhrsMode.DOF6) {
                val magneticYaw = headingCorrector.correctWithAccMagOrNull(
                    ax = frame.accX,
                    ay = frame.accY,
                    az = frame.accZ,
                    mx = frame.magX,
                    my = frame.magY,
                    mz = frame.magZ
                )
                if (magneticYaw != null) {
                    magneticYawHistory.addLast(magneticYaw)
                    while (magneticYawHistory.size > 25) {
                        magneticYawHistory.removeFirst()
                    }
                }
                stableMagneticYaw(currentYawRad = latestYawRad)
            } else {
                null
            }
            if (config.ahrsMode == com.example.pdr_zjj.ahrs.AhrsMode.DOF6 && stableMagneticYaw != null) {
                latestYawRad = fuseHeadingFor6Dof(
                    currentYawRad = latestYawRad,
                    magneticYawRad = stableMagneticYaw,
                    gyroMagnitude = gyroMagnitude,
                    continuousCorrection = true
                )
            }

            yawHistory.addLast(latestYawRad)
            while (yawHistory.size > 15) {
                yawHistory.removeFirst()
            }

            val stepEvent = stepDetector.update(
                timeSec = frame.relativeTimeSec,
                verticalLinearAcc = verticalLinearAcc,
                accMagnitude = accMagnitude,
                accZ = frame.accZ,
                gyroMagnitude = gyroMagnitude
            ) ?: continue

            latestYawRad = stabilizedStepYaw(latestYawRad, lastStepYawRad)
            collectStartupHeadingSample(latestYawRad)

            if (config.ahrsMode == com.example.pdr_zjj.ahrs.AhrsMode.DOF6 && stableMagneticYaw != null) {
                latestYawRad = fuseHeadingFor6Dof(
                    currentYawRad = latestYawRad,
                    magneticYawRad = stableMagneticYaw,
                    gyroMagnitude = gyroMagnitude
                )
                latestYawRad = stabilizedStepYaw(latestYawRad, lastStepYawRad)
            }

            if (carryMode == CarryMode.POCKET) {
                val walkingHeadingEstimate = walkingDirectionEstimator.estimateHeading(
                    referenceHeadingRad = QuaternionUtils.headingToEnuRad(latestYawRad)
                )
                if (walkingHeadingEstimate != null) {
                    val blendedHeading = PocketHeadingTuner.blendStepHeading(
                        currentHeadingRad = QuaternionUtils.headingToEnuRad(latestYawRad),
                        estimate = walkingHeadingEstimate
                    )
                    latestYawRad = QuaternionUtils.displayHeadingToInternalRad(blendedHeading)
                    latestYawRad = stabilizedStepYaw(latestYawRad, lastStepYawRad)
                }
            }

            val stepLengthMeter = stepLengthEstimator.estimate(
                currentStepTimeSec = stepEvent.timeSec,
                stepAmplitude = stepEvent.amplitude
            )
            val compensatedStepLengthMeter = compensateStepLengthForOffline9(
                rawStepLengthMeter = stepLengthMeter,
                stepAmplitude = stepEvent.amplitude,
                gyroMagnitude = gyroMagnitude
            )

            val interrupted = interruptDetector.update(stepEvent.timeSec)

            if (interrupted && config.ahrsMode == com.example.pdr_zjj.ahrs.AhrsMode.DOF6) {
                val correctedYaw = stableMagneticYaw(
                    currentYawRad = latestYawRad,
                    allowLargeGap = true
                )

                if (correctedYaw != null) {
                    latestYawRad = if (config.ahrsMode == com.example.pdr_zjj.ahrs.AhrsMode.DOF6) {
                        fuseHeadingFor6Dof(
                            currentYawRad = latestYawRad,
                            magneticYawRad = correctedYaw,
                            gyroMagnitude = gyroMagnitude,
                            forceStrongCorrection = true
                        )
                    } else {
                        fuseHeadingWithMagnetic(
                            latestYawRad,
                            correctedYaw,
                            forceStrongCorrection = true
                        )
                    }
                    latestYawRad = stabilizedStepYaw(latestYawRad, lastStepYawRad)
                }
            }

            lastStepYawRad = latestYawRad

            val startupSuppressionEnd = startupSuppressionEndTimeSec
            if (startupSuppressionEnd != null && stepEvent.timeSec < startupSuppressionEnd) {
                startupDiscardedStepCount += 1
                continue
            }

            if (shouldSuppressInitialPositionSteps()) {
                initialPositionSuppressedStepCount += 1
                if (initialPositionSuppressedStepCount <= initialPositionSuppressedSteps()) {
                    continue
                }
            }

            latestYawRad = applyStartupHeadingAssist(latestYawRad)

            val outputYawRad = when (config.ahrsMode) {
                com.example.pdr_zjj.ahrs.AhrsMode.DOF6 -> {
                    QuaternionUtils.normalizeAngle0To2Pi(
                        QuaternionUtils.headingToEnuRad(latestYawRad) - Math.PI * 0.5
                    )
                }
                com.example.pdr_zjj.ahrs.AhrsMode.DOF9 -> {
                    QuaternionUtils.headingToEnuRad(latestYawRad)
                }
            }

            val pos = positionUpdater.update(
                timeSec = stepEvent.timeSec,
                stepLengthMeter = compensatedStepLengthMeter,
                yawRad = outputYawRad
            )

            stepResults.add(
                OfflineStepResult(
                    stepIndex = (
                        stepEvent.stepIndex -
                            startupDiscardedStepCount -
                            suppressedInitialPositionStepOffset()
                        ).coerceAtLeast(1),
                    stepTimeSec = stepEvent.timeSec,
                    peakValue = stepEvent.peakValue,
                    stepLengthMeter = compensatedStepLengthMeter,
                    eastMeter = pos.eastMeter,
                    northMeter = pos.northMeter,
                    yawRad = outputYawRad,
                    interrupted = interrupted
                )
            )
            emittedPositionStepCount += 1
        }

        return OfflinePdrResult(
            steps = stepResults
        )
    }

    private fun createAhrsFilter(): IAhrsFilter {
        return when (config.ahrsMode) {
            com.example.pdr_zjj.ahrs.AhrsMode.DOF6 -> {
                Ahrs6DofMahony(
                    kp = 1.0,
                    ki = 0.015
                )
            }

            com.example.pdr_zjj.ahrs.AhrsMode.DOF9 -> {
                Ahrs9DofMahony(
                    kpAcc = 0.9,
                    kiAcc = 0.015,
                    kpMag = 0.12,
                    magNormToleranceRatio = 0.20,
                    magReferenceBlend = 0.01
                )
            }
        }
    }

    private fun stabilizedStepYaw(currentYawRad: Double, lastStepYawRad: Double?): Double {
        val smoothedYaw = QuaternionUtils.circularMean(yawHistory)
        val referenceYaw = if (yawHistory.isEmpty()) currentYawRad else smoothedYaw
        val lastYaw = lastStepYawRad ?: return referenceYaw
        return QuaternionUtils.limitAngleChange(
            previousRad = lastYaw,
            targetRad = referenceYaw,
            maxDeltaRad = Math.toRadians(maxHeadingDeltaDeg())
        )
    }

    private fun fuseHeadingWithMagnetic(
        currentYawRad: Double,
        magneticYawRad: Double,
        forceStrongCorrection: Boolean = false
    ): Double {
        val alpha = when {
            forceStrongCorrection && config.ahrsMode == com.example.pdr_zjj.ahrs.AhrsMode.DOF6 -> 0.55
            forceStrongCorrection -> 0.30
            config.ahrsMode == com.example.pdr_zjj.ahrs.AhrsMode.DOF6 -> 0.18
            else -> minOf(config.headingCorrectionBlendFactor, 0.12)
        }

        return QuaternionUtils.blendYaw(
            currentYawRad,
            magneticYawRad,
            alpha
        )
    }

    private fun fuseHeadingFor6Dof(
        currentYawRad: Double,
        magneticYawRad: Double,
        gyroMagnitude: Double,
        forceStrongCorrection: Boolean = false,
        continuousCorrection: Boolean = false
    ): Double {
        val rawDelta = QuaternionUtils.wrapToPi(magneticYawRad - currentYawRad)
        val maxCorrectionRad = Math.toRadians(
            when {
                forceStrongCorrection -> 36.0
                continuousCorrection -> 10.0
                else -> 20.0
            }
        )
        val limitedTarget = QuaternionUtils.normalizeAngle0To2Pi(
            currentYawRad + rawDelta.coerceIn(-maxCorrectionRad, maxCorrectionRad)
        )

        val headingErrorRad = abs(rawDelta)
        val baseAlpha = when {
            forceStrongCorrection -> 0.50
            continuousCorrection && headingErrorRad < Math.toRadians(8.0) -> 0.20
            continuousCorrection && headingErrorRad < Math.toRadians(16.0) -> 0.14
            continuousCorrection -> 0.08
            headingErrorRad < Math.toRadians(10.0) -> 0.38
            headingErrorRad < Math.toRadians(20.0) -> 0.28
            else -> 0.16
        }
        val turnScale = when {
            gyroMagnitude < 0.10 -> if (continuousCorrection) 1.15 else 1.30
            gyroMagnitude < 0.35 -> 1.0
            else -> if (continuousCorrection) 0.55 else 0.72
        }
        val alpha = (baseAlpha * turnScale).coerceIn(
            if (continuousCorrection) 0.04 else 0.10,
            when {
                forceStrongCorrection -> 0.60
                continuousCorrection -> 0.22
                else -> 0.45
            }
        )

        return QuaternionUtils.blendYaw(
            currentYawRad,
            limitedTarget,
            alpha
        )
    }

    private fun maxHeadingDeltaDeg(): Double {
        return when (config.ahrsMode) {
            com.example.pdr_zjj.ahrs.AhrsMode.DOF6 -> 18.0
            com.example.pdr_zjj.ahrs.AhrsMode.DOF9 -> 15.0
        }
    }

    private fun stableMagneticYaw(
        currentYawRad: Double? = null,
        allowLargeGap: Boolean = false
    ): Double? {
        if (magneticYawHistory.size < 6) return null

        val meanYaw = QuaternionUtils.circularMean(magneticYawHistory)
        val maxDeviationRad = magneticYawHistory.maxOfOrNull { sample ->
            abs(QuaternionUtils.wrapToPi(sample - meanYaw))
        } ?: return null

        val maxAllowedSpreadRad = Math.toRadians(
            when (config.ahrsMode) {
                com.example.pdr_zjj.ahrs.AhrsMode.DOF6 -> 28.0
                com.example.pdr_zjj.ahrs.AhrsMode.DOF9 -> 16.0
            }
        )
        if (maxDeviationRad > maxAllowedSpreadRad) return null

        if (currentYawRad != null) {
            val yawGapRad = abs(QuaternionUtils.wrapToPi(meanYaw - currentYawRad))
            val maxAllowedGapRad = Math.toRadians(
                when {
                    allowLargeGap && config.ahrsMode == com.example.pdr_zjj.ahrs.AhrsMode.DOF6 -> 45.0
                    allowLargeGap -> 28.0
                    config.ahrsMode == com.example.pdr_zjj.ahrs.AhrsMode.DOF6 -> 30.0
                    else -> 18.0
                }
            )
            if (yawGapRad > maxAllowedGapRad) return null
        }

        return meanYaw
    }

    private fun startupStepSuppressionSec(): Double {
        return when (config.ahrsMode) {
            com.example.pdr_zjj.ahrs.AhrsMode.DOF6 -> 0.0
            com.example.pdr_zjj.ahrs.AhrsMode.DOF9 -> 2.4
        }
    }

    private fun compensateStepLengthForOffline9(
        rawStepLengthMeter: Double,
        stepAmplitude: Double,
        gyroMagnitude: Double
    ): Double {
        if (config.ahrsMode != com.example.pdr_zjj.ahrs.AhrsMode.DOF9) {
            return rawStepLengthMeter
        }

        val amplitudeScale = when {
            stepAmplitude >= 0.55 -> 1.08
            stepAmplitude >= 0.40 -> 1.06
            stepAmplitude >= 0.28 -> 1.04
            else -> 1.02
        }
        val turnScale = when {
            gyroMagnitude >= 1.20 -> 0.92
            gyroMagnitude >= 0.85 -> 0.96
            else -> 1.0
        }

        val compensated = rawStepLengthMeter * amplitudeScale * turnScale
        val minStepLength = config.minStepLengthMeter
        val maxStepLength = maxOf(config.maxStepLengthMeter, 1.15)
        return compensated.coerceIn(minStepLength, maxStepLength)
    }

    private fun initialPositionSuppressedSteps(): Int {
        return when (config.ahrsMode) {
            com.example.pdr_zjj.ahrs.AhrsMode.DOF6 -> 0
            com.example.pdr_zjj.ahrs.AhrsMode.DOF9 -> 5
        }
    }

    private fun collectStartupHeadingSample(yawRad: Double) {
        if (config.ahrsMode != com.example.pdr_zjj.ahrs.AhrsMode.DOF9) return
        if (startupHeadingReferenceRad != null) return

        startupHeadingSamples.addLast(yawRad)
        while (startupHeadingSamples.size > STARTUP_HEADING_SAMPLE_WINDOW) {
            startupHeadingSamples.removeFirst()
        }

        if (startupHeadingSamples.size >= STARTUP_HEADING_MIN_SAMPLES) {
            startupHeadingReferenceRad = QuaternionUtils.circularMean(startupHeadingSamples)
        }
    }

    private fun applyStartupHeadingAssist(currentYawRad: Double): Double {
        if (config.ahrsMode != com.example.pdr_zjj.ahrs.AhrsMode.DOF9) return currentYawRad

        val referenceYaw = startupHeadingReferenceRad ?: return currentYawRad
        if (emittedPositionStepCount >= STARTUP_HEADING_LOCK_STEPS) return currentYawRad

        val progress = emittedPositionStepCount.toDouble() / STARTUP_HEADING_LOCK_STEPS.toDouble()
        val alpha = (0.72 - progress * 0.60).coerceIn(0.10, 0.72)
        return QuaternionUtils.blendYaw(currentYawRad, referenceYaw, alpha)
    }

    private fun shouldSuppressInitialPositionSteps(): Boolean {
        return initialPositionSuppressedSteps() > 0
    }

    private fun suppressedInitialPositionStepOffset(): Int {
        return minOf(initialPositionSuppressedStepCount, initialPositionSuppressedSteps())
    }

    private companion object {
        private const val INITIAL_STABILIZATION_SEC = 1.2
        private const val STARTUP_HEADING_SAMPLE_WINDOW = 8
        private const val STARTUP_HEADING_MIN_SAMPLES = 3
        private const val STARTUP_HEADING_LOCK_STEPS = 12
    }
}
