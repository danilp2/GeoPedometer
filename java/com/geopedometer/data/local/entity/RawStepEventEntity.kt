package com.geopedometer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "raw_step_events")
data class RawStepEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val confidence: Float,
    val stepLengthMeters: Double,
    val date: String
)