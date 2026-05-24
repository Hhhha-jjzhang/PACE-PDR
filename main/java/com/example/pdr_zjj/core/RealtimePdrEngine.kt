package com.example.pdr_zjj.core

import com.example.pdr_zjj.ahrs.IAhrsFilter
import com.example.pdr_zjj.ahrs.QuaternionUtils
import com.example.pdr_zjj.ahrs.AhrsMode
import com.example.pdr_zjj.mode.CarryMode
import com.example.pdr_zjj.position.HeadingCorrector
import com.example.pdr_zjj.position.PocketHeadingTuner
import com.example.pdr_zjj.position.PositionUpdater
import com.example.pdr_zjj.position.WalkingDirectionEstimator
import com.example.pdr_zjj.preprocess.model.SyncedSensorFrame
import com.example.pdr_zjj.step.InterruptDetector
import com.example.pdr_zjj.step.StepDetector
import com.example.pdr_zjj.step.StepLengthEstimator
import kotlin.math.abs
import kotlin.math.sqrt

class RealtimePdrEngine(
    private val ahrsFilter: IAhrsFilter,
    private val ahrsMode: AhrsMode,
    private val stepDetector: StepDetector,
    private val stepLengthEstimator: StepLengthEstimator,
    private val positionUpdater: PositionUpdater,
    private val interruptDetector: InterruptDetector,
    private val headingCorrector: HeadingCorrector,
    private val headingCorrectionBlendFactor: Double = 0.30,
    private val carryMode: CarryMode = CarryMode.HANDHELD
) {
    private var stabilizationEndTimeSec: Double? = null
    private var startupSuppressionEndTimeSec: Double? = null
    private var startupDiscardedStepCount = 0
    private var initialPositionSuppressedStepCount = 0
    private val yawHistory = ArrayDeque<Double>()
    private val magneticYawHistory = ArrayDeque<Double>()
    private var lastStepYawRad: Double? = null
    private val enableMagneticHeadingCorrection = ahrsMode == AhrsMode.DOF9
    private val walkingDirectionEstimator = WalkingDirectionEstimator()

    fun reset() {
        ahrsFilter.reset()
        stepDetector.reset()
        stepLengthEstimator.reset()
        positionUpdater.reset()
        interruptDetector.reset()
        yawHistory.clear()
        magneticYawHistory.clear()
        lastStepYawRad = null
        walkingDirectionEstimator.reset()
        stabilizationEndTimeSec = null
        startupSuppressionEndTimeSec = null
        startupDiscardedStepCount = 0
        initialPositionSuppressedStepCount = 0
    }

    fun applyInitialization(
        rollRad: Double,
        pitchRad: Double,
        headingRad: Double
    ) {
        val internalHeadingRad = QuaternionUtils.displayHeadingToInternalRad(headingRad)
        val yawRad = QuaternionUtils.compassHeadingToYawRad(internalHeadingRad)
        val q0 = QuaternionUtils.fromEuler(
            rollRad,
            pitchRad,
            yawRad
        )
        ahrsFilter.setInitialQuaternion(q0)
        stepDetector.reset()
        stepLengthEstimator.reset()
        positionUpdater.reset()
        interruptDetector.reset()
        yawHistory.clear()
        magneticYawHistory.clear()
        lastStepYawRad = null
        walkingDirectionEstimator.reset()
        stabilizationEndTimeSec = null
        startupSuppressionEndTimeSec = null
        startupDiscardedStepCount = 0
        initialPositionSuppressedStepCount = 0
    }

    fun processFrame(
        frame: SyncedSensorFrame,
        isInitialized: Boolean
    ): RealtimePdrResult? {
        if (!isInitialized) return null

        val pose = ahrsFilter.update(frame) ?: return null
        walkingDirectionEstimator.addFrame(
            quaternion = ahrsFilter.getQuaternion(),
            ax = frame.accX,
            ay = frame.accY,
            az = frame.accZ,
            rollRad = pose.rollRad,
            pitchRad = pose.pitchRad
        )

        var currentYawRad = pose.yawRad
        yawHistory.addLast(currentYawRad)
        while (yawHistory.size > 15) {
            yawHistory.removeFirst()
        }

        val settleEnd = stabilizationEndTimeSec ?: (frame.relativeTimeSec + INITIAL_STABILIZATION_SEC).also {
            stabilizationEndTimeSec = it
            startupSuppressionEndTimeSec = it + startupStepSuppressionSec()
        }

        val currentQuaternion = ahrsFilter.getQuaternion()
        val verticalLinearAcc = QuaternionUtils.verticalLinearAcceleration(
            q = currentQuaternion,
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

        var stableMagneticYaw: Double? = null
        if (enableMagneticHeadingCorrection) {
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
                while (magneticYawHistory.size > 12) {
                    magneticYawHistory.removeFirst()
                }
            }
            stableMagneticYaw = stableMagneticYaw()
            if (stableMagneticYaw != null) {
                currentYawRad = applyRealtimeHeadingAssist(
                    currentYawRad = currentYawRad,
                    magneticYawRad = stableMagneticYaw,
                    gyroMagnitude = gyroMagnitude
                )
            }
        }

        val displayYawRad = QuaternionUtils.headingToEnuRad(currentYawRad)
        if (frame.relativeTimeSec < settleEnd) {
            return RealtimePdrResult(
                rollRad = pose.rollRad,
                pitchRad = pose.pitchRad,
                yawRad = displayYawRad,
                stepResult = null
            )
        }

        val stepEvent = stepDetector.update(
            timeSec = frame.relativeTimeSec,
            verticalLinearAcc = verticalLinearAcc,
            accMagnitude = accMagnitude,
            accZ = frame.accZ,
            gyroMagnitude = gyroMagnitude
        )

        var stepResult: RealtimeStepResult? = null

        if (stepEvent != null) {
            currentYawRad = stabilizedStepYaw(currentYawRad)

            if (stableMagneticYaw != null) {
                currentYawRad = fuseHeadingWithMagnetic(currentYawRad, stableMagneticYaw)
                currentYawRad = stabilizedStepYaw(currentYawRad)
            }

            if (carryMode == CarryMode.POCKET) {
                val walkingHeadingEstimate = walkingDirectionEstimator.estimateHeading(
                    referenceHeadingRad = QuaternionUtils.headingToEnuRad(currentYawRad)
                )
                if (walkingHeadingEstimate != null) {
                    val blendedHeading = PocketHeadingTuner.blendStepHeading(
                        currentHeadingRad = QuaternionUtils.headingToEnuRad(currentYawRad),
                        estimate = walkingHeadingEstimate
                    )
                    currentYawRad = QuaternionUtils.displayHeadingToInternalRad(blendedHeading)
                    currentYawRad = stabilizedStepYaw(currentYawRad)
                }
            }

            val stepLengthMeter = stepLengthEstimator.estimate(
                currentStepTimeSec = stepEvent.timeSec,
                stepAmplitude = stepEvent.amplitude
            )

            val interrupted = interruptDetector.update(stepEvent.timeSec)

            if (interrupted && enableMagneticHeadingCorrection) {
                val correctedYaw = stableMagneticYaw()

                if (correctedYaw != null) {
                    currentYawRad = fuseHeadingWithMagnetic(
                        currentYawRad,
                        correctedYaw,
                        forceStrongCorrection = true
                    )
                    currentYawRad = stabilizedStepYaw(currentYawRad)
                }
            }

            lastStepYawRad = currentYawRad

            val outputYawRad = QuaternionUtils.headingToEnuRad(currentYawRad)

            val startupSuppressionEnd = startupSuppressionEndTimeSec
            if (startupSuppressionEnd != null && stepEvent.timeSec < startupSuppressionEnd) {
                startupDiscardedStepCount += 1
                return RealtimePdrResult(
                    rollRad = pose.rollRad,
                    pitchRad = pose.pitchRad,
                    yawRad = displayYawRad,
                    stepResult = null
                )
            }

            if (shouldSuppressInitialPositionSteps()) {
                initialPositionSuppressedStepCount += 1
                if (initialPositionSuppressedStepCount <= initialPositionSuppressedSteps()) {
                    return RealtimePdrResult(
                        rollRad = pose.rollRad,
                        pitchRad = pose.pitchRad,
                        yawRad = displayYawRad,
                        stepResult = null
                    )
                }
            }

            val pos = positionUpdater.update(
                timeSec = stepEvent.timeSec,
                stepLengthMeter = stepLengthMeter,
                yawRad = outputYawRad
            )

            stepResult = RealtimeStepResult(
                stepIndex = (
                    stepEvent.stepIndex -
                        startupDiscardedStepCount -
                        suppressedInitialPositionStepOffset()
                    ).coerceAtLeast(1),
                stepTimeSec = stepEvent.timeSec,
                peakValue = stepEvent.peakValue,
                stepLengthMeter = stepLengthMeter,
                eastMeter = pos.eastMeter,
                northMeter = pos.northMeter,
                yawRad = outputYawRad,
                interrupted = interrupted
            )
        }

        return RealtimePdrResult(
            rollRad = pose.rollRad,
            pitchRad = pose.pitchRad,
            yawRad = displayYawRad,
            stepResult = stepResult
        )
    }

    private companion object {
        private const val INITIAL_STABILIZATION_SEC = 1.2
    }

    private fun startupStepSuppressionSec(): Double {
        return when (ahrsMode) {
            AhrsMode.DOF6 -> 0.0
            AhrsMode.DOF9 -> 1.6
        }
    }

    private fun initialPositionSuppressedSteps(): Int {
        return when (ahrsMode) {
            AhrsMode.DOF6 -> 0
            AhrsMode.DOF9 -> 2
        }
    }

    private fun shouldSuppressInitialPositionSteps(): Boolean {
        return initialPositionSuppressedSteps() > 0
    }

    private fun suppressedInitialPositionStepOffset(): Int {
        return minOf(initialPositionSuppressedStepCount, initialPositionSuppressedSteps())
    }

    private fun stabilizedStepYaw(currentYawRad: Double): Double {
        val smoothedYaw = QuaternionUtils.circularMean(yawHistory)
        val referenceYaw = if (yawHistory.isEmpty()) currentYawRad else smoothedYaw
        val lastStepYaw = lastStepYawRad ?: return referenceYaw
        return QuaternionUtils.limitAngleChange(
            previousRad = lastStepYaw,
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
            forceStrongCorrection -> 0.80
            else -> headingCorrectionBlendFactor
        }

        return QuaternionUtils.blendYaw(
            currentYawRad,
            magneticYawRad,
            alpha
        )
    }

    private fun maxHeadingDeltaDeg(): Double {
        return when (ahrsMode) {
            AhrsMode.DOF6 -> 8.0
            AhrsMode.DOF9 -> 12.0
        }
    }

    private fun applyRealtimeHeadingAssist(
        currentYawRad: Double,
        magneticYawRad: Double,
        gyroMagnitude: Double
    ): Double {
        val alpha = when {
            gyroMagnitude < 0.08 -> 0.38
            gyroMagnitude < 0.18 -> 0.22
            gyroMagnitude < 0.30 -> 0.12
            else -> 0.0
        }
        if (alpha <= 0.0) return currentYawRad
        return QuaternionUtils.blendYaw(currentYawRad, magneticYawRad, alpha)
    }

    private fun stableMagneticYaw(): Double? {
        if (magneticYawHistory.size < 5) return null
        val meanYaw = QuaternionUtils.circularMean(magneticYawHistory)
        val maxDeviation = magneticYawHistory.maxOfOrNull {
            abs(QuaternionUtils.wrapToPi(it - meanYaw))
        } ?: return null
        if (maxDeviation > Math.toRadians(18.0)) return null
        return meanYaw
    }
}
