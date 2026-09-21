package com.geopedometer.data.sensor.processor

class LowPassFilter(private val alpha: Float = 0.1f) {
    private var lastOutput: Float = 0f
    private var initialized = false

    fun process(input: Float): Float {
        if (!initialized) {
            lastOutput = input
            initialized = true
            return input
        }
        lastOutput = alpha * input + (1f - alpha) * lastOutput
        return lastOutput
    }

    fun reset() {
        initialized = false
        lastOutput = 0f
    }
}