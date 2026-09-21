package com.geopedometer.data.sensor.validator

import com.geopedometer.domain.model.StepEvent
import kotlin.math.abs
import kotlin.math.sqrt

class AdaptivePeakStepValidator(
    private val minStepIntervalMs: Long = 300L,
    private val maxStepIntervalMs: Long = 2000L,
    private val periodicityWindowSize: Int = 6,
    private val thresholdMultiplier: Float = 1.2f,
    private val minThreshold: Float = 1.5f,
    private val maxPeriodicityVariation: Float = 0.35f
) {
    private val intervalBuffer = LongArray(periodicityWindowSize)
    private var intervalIndex = 0
    private var intervalCount = 0

    private var lastPeakTimestampNs: Long = 0L
    private var lastPeakValue: Float = 0f
    private var isRising = false
    private var runningMean: Float = 0f
    private var runningM2: Float = 0f
    private var runningCount: Int = 0

    private var prevValue: Float = 0f
    private var initialized = false

    fun validate(processedSignal: Float, timestampNs: Long): StepEvent? {
        if (!initialized) {
            prevValue = processedSignal
            runningMean = processedSignal
            initialized = true
            return null
        }

        updateRunningStats(processedSignal)

        val currentlyRising = processedSignal > prevValue
        prevValue = processedSignal

        if (isRising && !currentlyRising) {
            val peakValue = lastPeakValue
            val peakTimestampNs = lastPeakTimestampNs

            val adaptiveThreshold = computeAdaptiveThreshold()
            if (peakValue < adaptiveThreshold) {
                isRising = currentlyRising
                return null
            }

            val elapsedMs = (timestampNs - lastPeakTimestampNs) / 1_000_000L
            if (lastPeakTimestampNs > 0 && elapsedMs < minStepIntervalMs) {
                isRising = currentlyRising
                return null
            }

            if (lastPeakTimestampNs > 0 && elapsedMs > maxStepIntervalMs) {
                intervalCount = 0
                intervalIndex = 0
            }

            if (intervalCount >= 3) {
                addToIntervalBuffer(elapsedMs)
                if (!isPeriodic()) {
                    isRising = currentlyRising
                    return null
                }
            } else if (lastPeakTimestampNs > 0) {
                addToIntervalBuffer(elapsedMs)
            }

            val confidence = computeConfidence(peakValue, adaptiveThreshold, elapsedMs)
            lastPeakTimestampNs = timestampNs
            isRising = currentlyRising
            return StepEvent(
                timestampMs = peakTimestampNs / 1_000_000L,
                confidence = confidence
            )
        }

        isRising = currentlyRising
        if (currentlyRising) {
            lastPeakValue = processedSignal
            lastPeakTimestampNs = timestampNs
        }
        return null
    }

    private fun computeAdaptiveThreshold(): Float {
        if (runningCount < 10) return minThreshold
        val variance = runningM2 / runningCount
        val stddev = sqrt(variance)
        val threshold = runningMean + thresholdMultiplier * stddev
        return maxOf(threshold, minThreshold)
    }

    private fun updateRunningStats(value: Float) {
        runningCount++
        val delta = value - runningMean
        runningMean += delta / runningCount
        val delta2 = value - runningMean
        runningM2 += delta * delta2
        if (runningCount > 500) {
            runningCount /= 2
            runningM2 /= 2f
        }
    }

    private fun isPeriodic(): Boolean {
        if (intervalCount < 3) return true
        var sum = 0.0
        for (i in 0 until intervalCount) sum += intervalBuffer[i]
        val mean = sum / intervalCount
        if (mean == 0.0) return false

        var varianceSum = 0.0
        for (i in 0 until intervalCount) {
            val diff = intervalBuffer[i] - mean
            varianceSum += diff * diff
        }
        val stddev = sqrt(varianceSum / intervalCount)
        val cv = (stddev / mean).toFloat()
        return cv < maxPeriodicityVariation
    }

    private fun computeConfidence(peakValue: Float, threshold: Float, intervalMs: Long): Float {
        val amplitudeConf = minOf((peakValue - threshold) / threshold, 1f)
        val idealInterval = 550.0
        val timeConf = maxOf(0f, 1f - (abs(intervalMs - idealInterval) / 500f).toFloat())
        return (amplitudeConf * 0.5f + timeConf * 0.5f).coerceIn(0.3f, 1f)
    }

    private fun addToIntervalBuffer(intervalMs: Long) {
        intervalBuffer[intervalIndex] = intervalMs
        intervalIndex = (intervalIndex + 1) % periodicityWindowSize
        if (intervalCount < periodicityWindowSize) intervalCount++
    }

    fun reset() {
        intervalIndex = 0
        intervalCount = 0
        lastPeakTimestampNs = 0L
        lastPeakValue = 0f
        isRising = false
        runningMean = 0f
        runningM2 = 0f
        runningCount = 0
        prevValue = 0f
        initialized = false
    }
}
