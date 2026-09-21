package com.geopedometer.data.sensor.processor

import kotlin.math.sqrt

class GravityRemover {
    private val lpfX = LowPassFilter(0.1f)
    private val lpfY = LowPassFilter(0.1f)
    private val lpfZ = LowPassFilter(0.1f)

    fun removeGravity(rawValues: FloatArray): FloatArray {
        val gx = lpfX.process(rawValues[0])
        val gy = lpfY.process(rawValues[1])
        val gz = lpfZ.process(rawValues[2])

        rawValues[0] = rawValues[0] - gx
        rawValues[1] = rawValues[1] - gy
        rawValues[2] = rawValues[2] - gz
        return rawValues
    }

    fun computeMagnitude(linearAccel: FloatArray): Float {
        val x = linearAccel[0]
        val y = linearAccel[1]
        val z = linearAccel[2]
        return sqrt(x * x + y * y + z * z)
    }

    fun reset() {
        lpfX.reset()
        lpfY.reset()
        lpfZ.reset()
    }
}
