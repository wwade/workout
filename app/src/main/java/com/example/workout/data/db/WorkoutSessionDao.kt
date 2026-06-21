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

    @Transaction
    @Query("SELECT * FROM workout_sessions ORDER BY startedAt ASC, id ASC")
    abstract suspend fun getAllSessionDetails(): List<WorkoutSessionWithChildren>

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

    @Query(
        """
        SELECT
            ws.id AS workoutSessionId,
            ws.workoutNameSnapshot AS workoutName,
            COALESCE(ws.completedAt, ws.startedAt) AS completedAt,
            se.setIndex AS setIndex,
            se.repsActual AS repsActual,
            se.loadActual AS loadActual,
            se.notes AS notes,
            se.skipped AS skipped
        FROM set_entries se
        INNER JOIN exercise_sessions es ON es.id = se.exerciseSessionId
        INNER JOIN circuit_sessions cs ON cs.id = es.circuitSessionId
        INNER JOIN workout_sessions ws ON ws.id = cs.workoutSessionId
        WHERE es.exerciseTemplateId = :exerciseTemplateId
            AND ws.status = 'COMPLETED'
            AND ws.id = (
                SELECT ws2.id FROM workout_sessions ws2
                INNER JOIN circuit_sessions cs2 ON cs2.workoutSessionId = ws2.id
                INNER JOIN exercise_sessions es2 ON es2.circuitSessionId = cs2.id
                WHERE es2.exerciseTemplateId = :exerciseTemplateId
                    AND ws2.status = 'COMPLETED'
                ORDER BY ws2.completedAt DESC, ws2.id DESC
                LIMIT 1
            )
        ORDER BY se.setIndex ASC
        """,
    )
    abstract suspend fun getPreviousWorkoutSetEntries(
        exerciseTemplateId: Long,
    ): List<ExerciseSetHistoryProjection>

    @Query(
        """
        SELECT
            ws.id AS workoutSessionId,
            ws.workoutNameSnapshot AS workoutName,
            COALESCE(ws.completedAt, ws.startedAt) AS completedAt,
            se.setIndex AS setIndex,
            se.repsActual AS repsActual,
            se.loadActual AS loadActual,
            se.notes AS notes,
            se.skipped AS skipped
        FROM set_entries se
        INNER JOIN exercise_sessions es ON es.id = se.exerciseSessionId
        INNER JOIN circuit_sessions cs ON cs.id = es.circuitSessionId
        INNER JOIN workout_sessions ws ON ws.id = cs.workoutSessionId
        WHERE es.exerciseTemplateId = :exerciseTemplateId
            AND ws.status = 'COMPLETED'
        ORDER BY ws.completedAt DESC, ws.id DESC, se.setIndex ASC
        LIMIT :limit
        """,
    )
    abstract suspend fun getRecentCompletedSetEntries(
        exerciseTemplateId: Long,
        limit: Int,
    ): List<ExerciseSetHistoryProjection>

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
