package com.example.pdr_zjj.ahrs

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.abs

object QuaternionUtils {

    // App-level heading uses compass convention: 0 = north, clockwise positive.
    // Quaternion Euler yaw uses mathematical convention around Z.
    fun compassHeadingToYawRad(headingRad: Double): Double {
        return normalizeAngle0To2Pi(Math.PI * 0.5 - headingRad)
    }

    fun yawToCompassHeadingRad(yawRad: Double): Double {
        return normalizeAngle0To2Pi(Math.PI * 0.5 - yawRad)
    }

    fun displayHeadingToInternalRad(headingRad: Double): Double {
        return normalizeAngle0To2Pi(headingRad - Math.PI)
    }

    fun internalHeadingToDisplayRad(headingRad: Double): Double {
        return normalizeAngle0To2Pi(headingRad + Math.PI)
    }

    fun headingToEnuRad(headingRad: Double): Double {
        return internalHeadingToDisplayRad(headingRad)
    }

    fun fromEuler(roll: Double, pitch: Double, yaw: Double): Quaternion {
        val cr = cos(roll * 0.5)
        val sr = sin(roll * 0.5)
        val cp = cos(pitch * 0.5)
        val sp = sin(pitch * 0.5)
        val cy = cos(yaw * 0.5)
        val sy = sin(yaw * 0.5)

        val q = Quaternion(
            w = cr * cp * cy + sr * sp * sy,
            x = sr * cp * cy - cr * sp * sy,
            y = cr * sp * cy + sr * cp * sy,
            z = cr * cp * sy - sr * sp * cy
        )
        q.normalize()
        return q
    }

    fun toEuler(q: Quaternion): Triple<Double, Double, Double> {
        val roll = atan2(
            2.0 * (q.w * q.x + q.y * q.z),
            1.0 - 2.0 * (q.x * q.x + q.y * q.y)
        )

        val sinp = 2.0 * (q.w * q.y - q.z * q.x)
        val pitch = asin(max(-1.0, min(1.0, sinp)))

        val yaw = atan2(
            2.0 * (q.w * q.z + q.x * q.y),
            1.0 - 2.0 * (q.y * q.y + q.z * q.z)
        )

        return Triple(roll, pitch, yaw)
    }

    fun gravityUnitBody(q: Quaternion): Triple<Double, Double, Double> {
        val gx = 2.0 * (q.x * q.z - q.w * q.y)
        val gy = 2.0 * (q.w * q.x + q.y * q.z)
        val gz = q.w * q.w - q.x * q.x - q.y * q.y + q.z * q.z
        return Triple(gx, gy, gz)
    }

    fun verticalLinearAcceleration(
        q: Quaternion,
        ax: Double,
        ay: Double,
        az: Double,
        gravityMeterPerSec2: Double = 9.81
    ): Double {
        val gravity = gravityUnitBody(q)
        val projectedAcc = ax * gravity.first + ay * gravity.second + az * gravity.third
        return projectedAcc - gravityMeterPerSec2
    }

    fun rotateBodyToWorld(
        q: Quaternion,
        x: Double,
        y: Double,
        z: Double
    ): Triple<Double, Double, Double> {
        val r11 = 1.0 - 2.0 * (q.y * q.y + q.z * q.z)
        val r12 = 2.0 * (q.x * q.y - q.w * q.z)
        val r13 = 2.0 * (q.x * q.z + q.w * q.y)

        val r21 = 2.0 * (q.x * q.y + q.w * q.z)
        val r22 = 1.0 - 2.0 * (q.x * q.x + q.z * q.z)
        val r23 = 2.0 * (q.y * q.z - q.w * q.x)

        val r31 = 2.0 * (q.x * q.z - q.w * q.y)
        val r32 = 2.0 * (q.y * q.z + q.w * q.x)
        val r33 = 1.0 - 2.0 * (q.x * q.x + q.y * q.y)

        return Triple(
            r11 * x + r12 * y + r13 * z,
            r21 * x + r22 * y + r23 * z,
            r31 * x + r32 * y + r33 * z
        )
    }

    fun blendYaw(currentYawRad: Double, targetYawRad: Double, alpha: Double): Double {
        val blend = alpha.coerceIn(0.0, 1.0)
        val wrappedDelta = wrapToPi(targetYawRad - currentYawRad)
        return normalizeAngle0To2Pi(currentYawRad + wrappedDelta * blend)
    }

    fun normalizeAngle0To2Pi(angleRad: Double): Double {
        var angle = angleRad
        val twoPi = 2.0 * Math.PI
        while (angle < 0.0) angle += twoPi
        while (angle >= twoPi) angle -= twoPi
        return angle
    }

    fun wrapToPi(angleRad: Double): Double {
        var angle = angleRad
        val twoPi = 2.0 * Math.PI
        while (angle <= -Math.PI) angle += twoPi
        while (angle > Math.PI) angle -= twoPi
        return angle
    }

    fun circularMean(anglesRad: Iterable<Double>): Double {
        var sumSin = 0.0
        var sumCos = 0.0
        var count = 0
        for (angle in anglesRad) {
            sumSin += sin(angle)
            sumCos += cos(angle)
            count += 1
        }
        if (count == 0) return 0.0
        if (abs(sumSin) < 1e-9 && abs(sumCos) < 1e-9) return 0.0
        return normalizeAngle0To2Pi(atan2(sumSin, sumCos))
    }

    fun limitAngleChange(previousRad: Double, targetRad: Double, maxDeltaRad: Double): Double {
        val delta = wrapToPi(targetRad - previousRad)
        val limited = delta.coerceIn(-maxDeltaRad, maxDeltaRad)
        return normalizeAngle0To2Pi(previousRad + limited)
    }
}
