package com.example.workout.data.repository

import com.example.workout.data.db.SetEntryEntity
import com.example.workout.data.db.WorkoutSessionDao
import com.example.workout.data.db.WorkoutTemplateDao
import com.example.workout.domain.model.CompletedSessionListItem
import com.example.workout.domain.model.SetEntry
import com.example.workout.domain.model.SetEntryDraft
import com.example.workout.domain.model.SessionStatus
import com.example.workout.domain.model.WorkoutSessionDetail
import com.example.workout.domain.repository.SessionRepository
import com.example.workout.domain.usecase.SessionProgressCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSessionRepository(
    private val workoutTemplateDao: WorkoutTemplateDao,
    private val workoutSessionDao: WorkoutSessionDao,
) : SessionRepository {
    override fun observeActiveSessionId(): Flow<Long?> {
        return workoutSessionDao.observeActiveSession().map { it?.id }
    }

    override suspend fun startWorkout(workoutId: Long): Long {
        val activeSessionId = workoutSessionDao.getActiveSessionId()
        if (activeSessionId != null) {
            return activeSessionId
        }
        val workout = workoutTemplateDao.getWorkoutTemplateGraph(workoutId)
            ?: error("Workout $workoutId not found")
        return workoutSessionDao.startWorkoutSession(workout)
    }

    override suspend fun abandonSession(sessionId: Long) {
        workoutSessionDao.updateSessionStatus(
            sessionId = sessionId,
            status = SessionStatus.ABANDONED,
            completedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun saveRoundEntries(sessionId: Long, entries: List<SetEntryDraft>) {
        workoutSessionDao.upsertSetEntries(
            entries.map {
                SetEntryEntity(
                    exerciseSessionId = it.exerciseSessionId,
                    setIndex = it.setIndex,
                    repsActual = it.repsActual,
                    loadActual = it.loadActual,
                    notes = it.notes.trim(),
                    skipped = it.skipped,
                )
            },
        )
        val detail = getSessionDetail(sessionId) ?: return
        if (SessionProgressCalculator.isCompleted(detail) && detail.status != SessionStatus.COMPLETED) {
            workoutSessionDao.updateSessionStatus(
                sessionId = sessionId,
                status = SessionStatus.COMPLETED,
                completedAt = System.currentTimeMillis(),
            )
        }
    }

    override fun observeSessionDetail(sessionId: Long): Flow<WorkoutSessionDetail?> {
        return workoutSessionDao.observeSessionDetail(sessionId).map { it?.toDomain() }
    }

    override suspend fun getSessionDetail(sessionId: Long): WorkoutSessionDetail? {
        return workoutSessionDao.getSessionDetail(sessionId)?.toDomain()
    }

    override fun observeCompletedSessions(): Flow<List<CompletedSessionListItem>> {
        return workoutSessionDao.observeCompletedSessions().map { sessions ->
            sessions.map { session ->
                val detail = workoutSessionDao.getSessionDetail(session.id)
                session.toCompletedListItem(
                    circuitCount = detail?.circuits?.size ?: 0,
                )
            }
        }
    }

    override suspend fun getLatestCompletedSetEntry(
        exerciseTemplateId: Long,
        setIndex: Int,
    ): SetEntry? {
        return workoutSessionDao.getLatestCompletedSetEntry(
            exerciseTemplateId = exerciseTemplateId,
            setIndex = setIndex,
        )?.toDomain()
    }
}
