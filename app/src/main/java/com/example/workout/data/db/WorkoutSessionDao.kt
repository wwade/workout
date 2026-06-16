package dev.wwade.workout.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.wwade.workout.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    abstract fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    @Query("SELECT id FROM workout_sessions WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    abstract suspend fun getActiveSessionId(): Long?

    @Insert
    protected abstract suspend fun insertWorkoutSession(entity: WorkoutSessionEntity): Long

    @Insert
    protected abstract suspend fun insertCircuitSession(entity: CircuitSessionEntity): Long

    @Insert
    protected abstract suspend fun insertExerciseSession(entity: ExerciseSessionEntity): Long

    @Upsert
    abstract suspend fun upsertSetEntries(entries: List<SetEntryEntity>)

    @Query("UPDATE workout_sessions SET status = :status, completedAt = :completedAt WHERE id = :sessionId")
    abstract suspend fun updateSessionStatus(
        sessionId: Long,
        status: SessionStatus,
        completedAt: Long?,
    )

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    abstract fun observeSessionDetail(sessionId: Long): Flow<WorkoutSessionWithChildren?>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    abstract suspend fun getSessionDetail(sessionId: Long): WorkoutSessionWithChildren?

    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    abstract fun observeCompletedSessions(): Flow<List<WorkoutSessionEntity>>

    @Query(
        """
        SELECT se.* FROM set_entries se
        INNER JOIN exercise_sessions es ON es.id = se.exerciseSessionId
        INNER JOIN circuit_sessions cs ON cs.id = es.circuitSessionId
        INNER JOIN workout_sessions ws ON ws.id = cs.workoutSessionId
        WHERE es.exerciseTemplateId = :exerciseTemplateId
            AND se.setIndex = :setIndex
            AND ws.status = 'COMPLETED'
        ORDER BY ws.completedAt DESC
        LIMIT 1
        """,
    )
    abstract suspend fun getLatestCompletedSetEntry(
        exerciseTemplateId: Long,
        setIndex: Int,
    ): SetEntryEntity?

    @Transaction
    open suspend fun startWorkoutSession(template: WorkoutTemplateWithChildren): Long {
        val sessionId = insertWorkoutSession(
            WorkoutSessionEntity(
                workoutTemplateId = template.workout.id,
                workoutNameSnapshot = template.workout.name,
                startedAt = System.currentTimeMillis(),
                completedAt = null,
                status = SessionStatus.IN_PROGRESS,
            ),
        )

        template.circuits.sortedBy { it.circuit.sortOrder }.forEach { circuit ->
            val sortedExercises = circuit.exercises.sortedBy { it.sortOrder }
            val circuitSessionId = insertCircuitSession(
                CircuitSessionEntity(
                    workoutSessionId = sessionId,
                    circuitTemplateId = circuit.circuit.id,
                    circuitNameSnapshot = circuit.circuit.name,
                    sortOrder = circuit.circuit.sortOrder,
                    setCount = sortedExercises.firstOrNull()?.setCount ?: 0,
                ),
            )
            sortedExercises.forEach { exercise ->
                insertExerciseSession(
                    ExerciseSessionEntity(
                        circuitSessionId = circuitSessionId,
                        exerciseTemplateId = exercise.id,
                        exerciseNameSnapshot = exercise.name,
                        guidanceSnapshot = exercise.guidance,
                        repMinSnapshot = exercise.repMin,
                        repMaxSnapshot = exercise.repMax,
                        loadKindSnapshot = exercise.loadKind,
                        loadMinSnapshot = exercise.loadMin,
                        loadMaxSnapshot = exercise.loadMax,
                        loadUnitSnapshot = exercise.loadUnit,
                        restTimeSecondsSnapshot = exercise.restTimeSeconds,
                        sortOrder = exercise.sortOrder,
                    ),
                )
            }
        }
        return sessionId
    }
}
