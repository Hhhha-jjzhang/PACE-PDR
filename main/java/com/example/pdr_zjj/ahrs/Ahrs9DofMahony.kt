package com.example.pdr_zjj.ahrs

import com.example.pdr_zjj.data.model.AhrsPose
import com.example.pdr_zjj.preprocess.model.SyncedSensorFrame
import kotlin.math.abs
import kotlin.math.sqrt

class Ahrs9DofMahony(
    private val kpAcc: Double = 1.0,
    private val kiAcc: Double = 0.02,
    private val kpMag: Double = 0.8,
    private val magNormToleranceRatio: Double = 0.35,
    private val magReferenceBlend: Double = 0.02
) : IAhrsFilter {

    private var q = Quaternion()
    private var eIntX = 0.0
    private var eIntY = 0.0
    private var eIntZ = 0.0
    private var magNormReference: Double? = null

    override fun reset(initialQuaternion: Quaternion) {
        q = initialQuaternion.copySelf()
        q.normalize()
        eIntX = 0.0
        eIntY = 0.0
        eIntZ = 0.0
        magNormReference = null
    }

    override fun setInitialQuaternion(q: Quaternion) {
        this.q = q.copySelf()
        this.q.normalize()
        eIntX = 0.0
        eIntY = 0.0
        eIntZ = 0.0
        magNormReference = null
    }

    override fun getQuaternion(): Quaternion {
        return q.copySelf()
    }

    override fun update(frame: SyncedSensorFrame): AhrsPose? {
        val dtSec = frame.dtSec
        if (dtSec <= 0.0) return null

        var gx = frame.gyroX
        var gy = frame.gyroY
        var gz = frame.gyroZ

        val ax = frame.accX
        val ay = frame.accY
        val az = frame.accZ

        val mx = frame.magX
        val my = frame.magY
        val mz = frame.magZ
        val gyroMagnitude = sqrt(gx * gx + gy * gy + gz * gz)

        val q0 = q.w
        val q1 = q.x
        val q2 = q.y
        val q3 = q.z

        var ex = 0.0
        var ey = 0.0
        var ez = 0.0

        // ---------- 1. 加速度约束：重力方向 ----------
        val accNorm = sqrt(ax * ax + ay * ay + az * az)
        if (accNorm > 1e-8) {
            val axn = ax / accNorm
            val ayn = ay / accNorm
            val azn = az / accNorm

            val vx = 2.0 * (q1 * q3 - q0 * q2)
            val vy = 2.0 * (q0 * q1 + q2 * q3)
            val vz = q0 * q0 - q1 * q1 - q2 * q2 + q3 * q3

            ex += ayn * vz - azn * vy
            ey += azn * vx - axn * vz
            ez += axn * vy - ayn * vx
        }

        // ---------- 2. 磁力计约束：航向方向 ----------
        val magNorm = sqrt(mx * mx + my * my + mz * mz)
        if (shouldUseMagneticCorrection(magNorm)) {
            val effectiveKpMag = kpMag * magneticGainScale(gyroMagnitude)
            val mxn = mx / magNorm
            val myn = my / magNorm
            val mzn = mz / magNorm

            // 当前姿态下，机体系磁场旋到导航系
            val hx = 2.0 * mxn * (0.5 - q2 * q2 - q3 * q3) +
                    2.0 * myn * (q1 * q2 - q0 * q3) +
                    2.0 * mzn * (q1 * q3 + q0 * q2)

            val hy = 2.0 * mxn * (q1 * q2 + q0 * q3) +
                    2.0 * myn * (0.5 - q1 * q1 - q3 * q3) +
                    2.0 * mzn * (q2 * q3 - q0 * q1)

            val bx = sqrt(hx * hx + hy * hy)
            val bz = 2.0 * mxn * (q1 * q3 - q0 * q2) +
                    2.0 * myn * (q2 * q3 + q0 * q1) +
                    2.0 * mzn * (0.5 - q1 * q1 - q2 * q2)

            val wx = 2.0 * bx * (0.5 - q2 * q2 - q3 * q3) +
                    2.0 * bz * (q1 * q3 - q0 * q2)

            val wy = 2.0 * bx * (q1 * q2 - q0 * q3) +
                    2.0 * bz * (q0 * q1 + q2 * q3)

            val wz = 2.0 * bx * (q0 * q2 + q1 * q3) +
                    2.0 * bz * (0.5 - q1 * q1 - q2 * q2)

            ex += effectiveKpMag * (myn * wz - mzn * wy)
            ey += effectiveKpMag * (mzn * wx - mxn * wz)
            ez += effectiveKpMag * (mxn * wy - myn * wx)
        }

        eIntX += ex * kiAcc * dtSec
        eIntY += ey * kiAcc * dtSec
        eIntZ += ez * kiAcc * dtSec

        gx += kpAcc * ex + eIntX
        gy += kpAcc * ey + eIntY
        gz += kpAcc * ez + eIntZ

        val halfDt = 0.5 * dtSec

        q.w = q0 + (-q1 * gx - q2 * gy - q3 * gz) * halfDt
        q.x = q1 + ( q0 * gx + q2 * gz - q3 * gy) * halfDt
        q.y = q2 + ( q0 * gy - q1 * gz + q3 * gx) * halfDt
        q.z = q3 + ( q0 * gz + q1 * gy - q2 * gx) * halfDt

        q.normalize()

        val euler = QuaternionUtils.toEuler(q)
        val heading = QuaternionUtils.yawToCompassHeadingRad(euler.third)

        return AhrsPose(
            time = frame.relativeTimeSec,
            qw = q.w,
            qx = q.x,
            qy = q.y,
            qz = q.z,
            rollRad = euler.first,
            pitchRad = euler.second,
            yawRad = heading
        )
    }

    private fun shouldUseMagneticCorrection(magNorm: Double): Boolean {
        if (magNorm <= 1e-8) return false

        val reference = magNormReference
        if (reference == null) {
            magNormReference = magNorm
            return true
        }

        val toleranceBase = reference.coerceAtLeast(1e-6)
        val relativeDeviation = abs(magNorm - reference) / toleranceBase
        if (relativeDeviation > magNormToleranceRatio) {
            return false
        }

        magNormReference = reference + (magNorm - reference) * magReferenceBlend.coerceIn(0.0, 1.0)
        return true
    }

    private fun magneticGainScale(gyroMagnitude: Double): Double {
        return when {
            gyroMagnitude < 0.08 -> 3.0
            gyroMagnitude < 0.18 -> 2.2
            gyroMagnitude < 0.35 -> 1.5
            else -> 1.0
        }
    }
}
