package com.geopedometer.domain.model

sealed class StepDataState {
    data object Idle : StepDataState()

    data class Counting(
        val steps: Long,
        val distanceMeters: Double,
        val distanceKm: Double,
        val activeTimeSeconds: Long,
        val stepLengthMeters: Double,
        val todayDate: String
    ) : StepDataState()

    data class GoalReached(
        val geoTargetId: String,
        val targetName: String,
        val totalSteps: Long,
        val totalDistanceKm: Double
    ) : StepDataState()

    data class Error(val message: String, val recoverable: Boolean) : StepDataState()
}