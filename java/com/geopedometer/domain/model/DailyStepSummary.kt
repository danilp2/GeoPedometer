package com.geopedometer.domain.model

data class DailyStepSummary(
    val date: String,
    val totalSteps: Long,
    val totalDistanceMeters: Double,
    val activeTimeSeconds: Long,
    val avgStepLengthMeters: Double
)
