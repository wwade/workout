package dev.wwade.workout.domain.repository

import dev.wwade.workout.domain.model.CompletedSessionListItem
import dev.wwade.workout.domain.model.ActiveSessionSummary
import dev.wwade.workout.domain.model.ExerciseSetHistoryItem
import dev.wwade.workout.domain.model.SetEntry
import dev.wwade.workout.domain.model.SetEntryDraft
import dev.wwade.workout.domain.model.WorkoutSessionDetail
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeActiveSession(): Flow<ActiveSessionSummary?>
    suspend fun startWorkout(workoutId: Long): Long
    suspend fun abandonSession(sessionId: Long)
    suspend fun saveRoundEntries(sessionId: Long, entries: List<SetEntryDraft>): Boolean
    fun observeSessionDetail(sessionId: Long): Flow<WorkoutSessionDetail?>
    suspend fun getSessionDetail(sessionId: Long): WorkoutSessionDetail?
    fun observeCompletedSessions(): Flow<List<CompletedSessionListItem>>
    suspend fun deleteCompletedSessions(sessionIds: Set<Long>)
    suspend fun getLatestCompletedSetEntry(exerciseDefinitionId: Long, setIndex: Int): SetEntry?
    suspend fun getPreviousWorkoutSetEntries(exerciseDefinitionId: Long): List<ExerciseSetHistoryItem>
    suspend fun getRecentCompletedSetEntries(
        exerciseDefinitionId: Long,
        limit: Int,
    ): List<ExerciseSetHistoryItem>
}
