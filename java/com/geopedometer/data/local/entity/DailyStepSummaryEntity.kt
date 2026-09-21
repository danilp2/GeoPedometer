package com.geopedometer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_step_summary")
data class DailyStepSummaryEntity(
    @PrimaryKey val date: String,
    val totalSteps: Long = 0,
    val totalDistanceMeters: Double = 0.0,
    val activeTimeSeconds: Long = 0,
    val avgStepLengthMeters: Double = 0.8,
    val updatedAt: Long = System.currentTimeMillis()
)
