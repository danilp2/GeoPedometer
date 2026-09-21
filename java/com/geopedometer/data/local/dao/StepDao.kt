package com.geopedometer.data.local.dao

import androidx.room.*
import com.geopedometer.data.local.entity.DailyStepSummaryEntity
import com.geopedometer.data.local.entity.RawStepEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {

    @Query("SELECT * FROM daily_step_summary WHERE date = :date")
    suspend fun getDailySummary(date: String): DailyStepSummaryEntity?

    @Query("SELECT * FROM daily_step_summary WHERE date BETWEEN :fromDate AND :toDate ORDER BY date DESC")
    fun getHistory(fromDate: String, toDate: String): Flow<List<DailyStepSummaryEntity>>

    @Transaction
    suspend fun upsertDailySummary(
        date: String,
        stepsToAdd: Long,
        distanceToAdd: Double,
        activeSecondsToAdd: Long,
        stepLength: Double
    ) {
        val existing = getDailySummary(date)
        if (existing == null) {
            insertDailySummary(
                DailyStepSummaryEntity(
                    date = date,
                    totalSteps = stepsToAdd,
                    totalDistanceMeters = distanceToAdd,
                    activeTimeSeconds = activeSecondsToAdd,
                    avgStepLengthMeters = stepLength
                )
            )
        } else {
            updateDailySummary(
                date = date,
                totalSteps = existing.totalSteps + stepsToAdd,
                totalDistanceMeters = existing.totalDistanceMeters + distanceToAdd,
                activeTimeSeconds = existing.activeTimeSeconds + activeSecondsToAdd,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailySummary(summary: DailyStepSummaryEntity)

    @Query("""
        UPDATE daily_step_summary 
        SET totalSteps = :totalSteps, 
            totalDistanceMeters = :totalDistanceMeters, 
            activeTimeSeconds = :activeTimeSeconds,
            updatedAt = :updatedAt
        WHERE date = :date
    """)
    suspend fun updateDailySummary(
        date: String,
        totalSteps: Long,
        totalDistanceMeters: Double,
        activeTimeSeconds: Long,
        updatedAt: Long
    )

    @Insert
    suspend fun insertRawEvent(event: RawStepEventEntity)

    @Query("DELETE FROM raw_step_events WHERE date < :cutoffDate")
    suspend fun deleteRawEventsBefore(cutoffDate: String): Int
}
