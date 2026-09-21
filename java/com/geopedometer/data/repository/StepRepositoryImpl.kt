package com.geopedometer.data.repository

import com.geopedometer.data.local.PedometerDatabase
import com.geopedometer.data.local.entity.RawStepEventEntity
import com.geopedometer.domain.model.DailyStepSummary
import com.geopedometer.domain.model.StepDataState
import com.geopedometer.domain.model.UserProfile
import com.geopedometer.domain.repository.StepRepository
import com.geopedometer.service.PedometerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepRepositoryImpl @Inject constructor(
    private val database: PedometerDatabase,
    private val profileStore: ProfileStore
) : StepRepository {

    private val dao = database.stepDao()
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    override val currentState: Flow<StepDataState>
        get() = PedometerService.serviceState

    override suspend fun getDailySummary(date: String): DailyStepSummary? {
        return withContext(Dispatchers.IO) {
            dao.getDailySummary(date)?.let {
                DailyStepSummary(
                    date = it.date,
                    totalSteps = it.totalSteps,
                    totalDistanceMeters = it.totalDistanceMeters,
                    activeTimeSeconds = it.activeTimeSeconds,
                    avgStepLengthMeters = it.avgStepLengthMeters
                )
            }
        }
    }

    override fun getHistory(fromDate: String, toDate: String): Flow<List<DailyStepSummary>> {
        return dao.getHistory(fromDate, toDate)
            .flowOn(Dispatchers.IO)
            .map { list ->
                list.map {
                    DailyStepSummary(
                        date = it.date,
                        totalSteps = it.totalSteps,
                        totalDistanceMeters = it.totalDistanceMeters,
                        activeTimeSeconds = it.activeTimeSeconds,
                        avgStepLengthMeters = it.avgStepLengthMeters
                    )
                }
            }
    }

    override suspend fun recordStep(timestampMs: Long, confidence: Float) {
        withContext(Dispatchers.IO) {
            val profile = profileStore.getProfile()
            val today = LocalDate.now().format(dateFmt)

            dao.upsertDailySummary(
                date = today,
                stepsToAdd = 1,
                distanceToAdd = profile.stepLengthMeters,
                activeSecondsToAdd = 0,
                stepLength = profile.stepLengthMeters
            )

            dao.insertRawEvent(
                RawStepEventEntity(
                    timestampMs = timestampMs,
                    confidence = confidence,
                    stepLengthMeters = profile.stepLengthMeters,
                    date = today
                )
            )
        }
    }

    override suspend fun updateProfile(profile: UserProfile) {
        withContext(Dispatchers.IO) { profileStore.saveProfile(profile) }
    }

    override suspend fun getProfile(): UserProfile {
        return withContext(Dispatchers.IO) { profileStore.getProfile() }
    }

    override suspend fun notifyGoalReached(targetId: String, targetName: String) {
        // Заглушка для будущего модуля целей
    }

    override suspend fun purgeOldRawEvents() {
        withContext(Dispatchers.IO) {
            val cutoff = LocalDate.now().minusDays(7).format(dateFmt)
            dao.deleteRawEventsBefore(cutoff)
        }
    }
}