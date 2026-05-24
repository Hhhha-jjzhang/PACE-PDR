package com.example.pdr_zjj.ahrs

data class Quaternion(
    var w: Double = 1.0,
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0
) {
    fun normalize() {
        val norm = kotlin.math.sqrt(w * w + x * x + y * y + z * z)
        if (norm > 1e-12) {
            w /= norm
            x /= norm
            y /= norm
            z /= norm
        }
    }

    fun copySelf(): Quaternion = Quaternion(w, x, y, z)
}