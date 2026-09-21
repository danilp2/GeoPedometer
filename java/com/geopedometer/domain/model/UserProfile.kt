package com.geopedometer.domain.model

data class UserProfile(
    val stepLengthMeters: Double = 0.8,
    val heightCm: Double = 175.0
) {
    companion object {
        fun estimateStepLength(heightCm: Double): Double = heightCm * 0.00415
    }
}