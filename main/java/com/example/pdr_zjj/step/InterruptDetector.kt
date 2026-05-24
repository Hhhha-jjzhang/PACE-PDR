package com.example.pdr_zjj.step

class InterruptDetector(
    private val interruptThresholdSec: Double = 1.5
) {
    private var lastStepTimeSec: Double? = null

    fun reset() {
        lastStepTimeSec = null
    }

    fun update(currentStepTimeSec: Double): Boolean {
        val last = lastStepTimeSec
        lastStepTimeSec = currentStepTimeSec

        if (last == null) return false
        return (currentStepTimeSec - last) > interruptThresholdSec
    }
}