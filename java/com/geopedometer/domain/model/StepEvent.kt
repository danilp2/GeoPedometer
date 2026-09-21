package com.geopedometer.domain.model

data class StepEvent(
    val timestampMs: Long,
    val confidence: Float
)