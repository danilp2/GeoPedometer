package com.geopedometer.domain.repository

import com.geopedometer.domain.model.DailyStepSummary
import com.geopedometer.domain.model.StepDataState
import com.geopedometer.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface StepRepository {
    val currentState: Flow<StepDataState>
    suspend fun getDailySummary(date: String): DailyStepSummary?
    fun getHistory(fromDate: String, toDate: String): Flow<List<DailyStepSummary>>
    suspend fun recordStep(timestampMs: Long, confidence: Float)
    suspend fun updateProfile(profile: UserProfile)
    suspend fun getProfile(): UserProfile
    suspend fun notifyGoalReached(targetId: String, targetName: String)
    suspend fun purgeOldRawEvents()
}
