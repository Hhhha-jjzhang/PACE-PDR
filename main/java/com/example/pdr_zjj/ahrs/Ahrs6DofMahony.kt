package com.example.pdr_zjj.ahrs

import com.example.pdr_zjj.data.model.AhrsPose
import com.example.pdr_zjj.preprocess.model.SyncedSensorFrame
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

class Ahrs6DofMahony(
    private val kp: Double = 1.2,
    private val ki: Double = 0.02
) : IAhrsFilter {

    private var q = Quaternion()
    private var eIntX = 0.0
    private var eIntY = 0.0
    private var eIntZ = 0.0

    override fun reset(initialQuaternion: Quaternion) {
        q = initialQuaternion.copySelf()
        q.normalize()
        eIntX = 0.0
        eIntY = 0.0
        eIntZ = 0.0
    }

    override fun setInitialQuaternion(q: Quaternion) {
        this.q = q.copySelf()
        this.q.normalize()
        eIntX = 0.0
        eIntY = 0.0
        eIntZ = 0.0
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

        val accNorm = sqrt(ax * ax + ay * ay + az * az)
        if (accNorm > 1e-8) {
            val axn = ax / accNorm
            val ayn = ay / accNorm
            val azn = az / accNorm

            val q0 = q.w
            val q1 = q.x
            val q2 = q.y
            val q3 = q.z

            // 当前四元数估计的重力方向
            val vx = 2.0 * (q1 * q3 - q0 * q2)
            val vy = 2.0 * (q0 * q1 + q2 * q3)
            val vz = q0 * q0 - q1 * q1 - q2 * q2 + q3 * q3

            // 误差叉积
            val ex = ayn * vz - azn * vy
            val ey = azn * vx - axn * vz
            val ez = axn * vy - ayn * vx

            eIntX += ex * ki * dtSec
            eIntY += ey * ki * dtSec
            eIntZ += ez * ki * dtSec

            gx += kp * ex + eIntX
            gy += kp * ey + eIntY
            gz += kp * ez + eIntZ
        }

        val halfDt = 0.5 * dtSec

        val q0 = q.w
        val q1 = q.x
        val q2 = q.y
        val q3 = q.z

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
}
