package com.example.workout.domain.repository

import com.example.workout.domain.model.CompletedSessionListItem
import com.example.workout.domain.model.SetEntry
import com.example.workout.domain.model.SetEntryDraft
import com.example.workout.domain.model.WorkoutSessionDetail
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeActiveSessionId(): Flow<Long?>
    suspend fun startWorkout(workoutId: Long): Long
    suspend fun abandonSession(sessionId: Long)
    suspend fun saveRoundEntries(sessionId: Long, entries: List<SetEntryDraft>)
    fun observeSessionDetail(sessionId: Long): Flow<WorkoutSessionDetail?>
    suspend fun getSessionDetail(sessionId: Long): WorkoutSessionDetail?
    fun observeCompletedSessions(): Flow<List<CompletedSessionListItem>>
    suspend fun getLatestCompletedSetEntry(exerciseTemplateId: Long, setIndex: Int): SetEntry?
}
