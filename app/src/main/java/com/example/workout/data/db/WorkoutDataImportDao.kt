package dev.wwade.workout.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WorkoutDataImportDao {
    @Query("DELETE FROM set_entries")
    suspend fun deleteSetEntries()

    @Query("DELETE FROM exercise_sessions")
    suspend fun deleteExerciseSessions()

    @Query("DELETE FROM circuit_sessions")
    suspend fun deleteCircuitSessions()

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteWorkoutSessions()

    @Query("DELETE FROM exercise_templates")
    suspend fun deleteExerciseTemplates()

    @Query("DELETE FROM circuit_templates")
    suspend fun deleteCircuitTemplates()

    @Query("DELETE FROM workout_templates")
    suspend fun deleteWorkoutTemplates()

    @Query("DELETE FROM exercise_definitions")
    suspend fun deleteExerciseDefinitions()

    @Insert
    suspend fun insertExerciseDefinitions(definitions: List<ExerciseDefinitionEntity>)

    @Insert
    suspend fun insertWorkoutTemplates(workouts: List<WorkoutTemplateEntity>)

    @Insert
    suspend fun insertCircuitTemplates(circuits: List<CircuitTemplateEntity>)

    @Insert
    suspend fun insertExerciseTemplates(exercises: List<ExerciseTemplateEntity>)

    @Insert
    suspend fun insertWorkoutSessions(sessions: List<WorkoutSessionEntity>)

    @Insert
    suspend fun insertCircuitSessions(circuits: List<CircuitSessionEntity>)

    @Insert
    suspend fun insertExerciseSessions(exercises: List<ExerciseSessionEntity>)

    @Insert
    suspend fun insertSetEntries(setEntries: List<SetEntryEntity>)
}
