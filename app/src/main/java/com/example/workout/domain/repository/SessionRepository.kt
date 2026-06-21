package dev.wwade.workout.domain.repository

import dev.wwade.workout.domain.model.CompletedSessionListItem
import dev.wwade.workout.domain.model.ExerciseSetHistoryItem
import dev.wwade.workout.domain.model.SetEntry
import dev.wwade.workout.domain.model.SetEntryDraft
import dev.wwade.workout.domain.model.WorkoutSessionDetail
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
    suspend fun getPreviousWorkoutSetEntries(exerciseTemplateId: Long): List<ExerciseSetHistoryItem>
    suspend fun getRecentCompletedSetEntries(
        exerciseTemplateId: Long,
        limit: Int,
    ): List<ExerciseSetHistoryItem>
}
