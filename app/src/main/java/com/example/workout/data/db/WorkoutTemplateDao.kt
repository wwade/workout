package dev.wwade.workout.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.wwade.workout.domain.model.CircuitDraft
import dev.wwade.workout.domain.model.ExerciseDraft
import dev.wwade.workout.domain.model.WorkoutDraft
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutTemplateDao {
    @Query("SELECT * FROM workout_templates ORDER BY updatedAt DESC, sortOrder ASC")
    abstract fun observeWorkoutTemplates(): Flow<List<WorkoutTemplateEntity>>

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE id = :workoutId")
    abstract suspend fun getWorkoutTemplateGraph(workoutId: Long): WorkoutTemplateWithChildren?

    @Query("SELECT * FROM workout_templates WHERE id = :workoutId")
    abstract suspend fun getWorkoutTemplateEntity(workoutId: Long): WorkoutTemplateEntity?

    @Insert
    protected abstract suspend fun insertWorkout(entity: WorkoutTemplateEntity): Long

    @Update
    protected abstract suspend fun updateWorkout(entity: WorkoutTemplateEntity)

    @Insert
    protected abstract suspend fun insertCircuit(entity: CircuitTemplateEntity): Long

    @Update
    protected abstract suspend fun updateCircuit(entity: CircuitTemplateEntity)

    @Insert
    protected abstract suspend fun insertExercise(entity: ExerciseTemplateEntity): Long

    @Update
    protected abstract suspend fun updateExercise(entity: ExerciseTemplateEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM workout_templates")
    protected abstract suspend fun getMaxWorkoutSortOrder(): Int

    @Query("SELECT COUNT(*) FROM workout_templates")
    abstract suspend fun countWorkoutTemplates(): Int

    @Query("SELECT id FROM circuit_templates WHERE workoutId = :workoutId")
    protected abstract suspend fun getCircuitIdsForWorkout(workoutId: Long): List<Long>

    @Query("SELECT id FROM exercise_templates WHERE circuitId = :circuitId")
    protected abstract suspend fun getExerciseIdsForCircuit(circuitId: Long): List<Long>

    @Query("DELETE FROM exercise_templates WHERE id IN (:exerciseIds)")
    protected abstract suspend fun deleteExercisesByIds(exerciseIds: List<Long>)

    @Query("DELETE FROM circuit_templates WHERE id IN (:circuitIds)")
    protected abstract suspend fun deleteCircuitsByIds(circuitIds: List<Long>)

    @Query("DELETE FROM workout_templates WHERE id = :workoutId")
    abstract suspend fun deleteWorkoutById(workoutId: Long)

    @Delete
    abstract suspend fun deleteWorkout(entity: WorkoutTemplateEntity)

    @Transaction
    open suspend fun upsertWorkoutGraph(workout: WorkoutDraft): Long {
        val now = System.currentTimeMillis()
        val workoutId = if (workout.id == null) {
            insertWorkout(
                WorkoutTemplateEntity(
                    name = workout.name.trim(),
                    sortOrder = getMaxWorkoutSortOrder() + 1,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            val existing = getWorkoutTemplateEntity(workout.id)
                ?: error("Missing workout ${workout.id}")
            updateWorkout(
                existing.copy(
                    name = workout.name.trim(),
                    updatedAt = now,
                ),
            )
            workout.id
        }

        val remainingCircuitIds = getCircuitIdsForWorkout(workoutId).toMutableSet()
        workout.circuits.forEachIndexed { circuitIndex, circuit ->
            val circuitId = upsertCircuit(
                workoutId = workoutId,
                circuit = circuit,
                sortOrder = circuitIndex,
            )
            remainingCircuitIds.remove(circuitId)
        }
        if (remainingCircuitIds.isNotEmpty()) {
            deleteCircuitsByIds(remainingCircuitIds.toList())
        }
        return workoutId
    }

    @Transaction
    protected open suspend fun upsertCircuit(
        workoutId: Long,
        circuit: CircuitDraft,
        sortOrder: Int,
    ): Long {
        val circuitId = if (circuit.id == null) {
            insertCircuit(
                CircuitTemplateEntity(
                    workoutId = workoutId,
                    name = circuit.name.trim(),
                    sortOrder = sortOrder,
                ),
            )
        } else {
            updateCircuit(
                CircuitTemplateEntity(
                    id = circuit.id,
                    workoutId = workoutId,
                    name = circuit.name.trim(),
                    sortOrder = sortOrder,
                ),
            )
            circuit.id
        }

        val remainingExerciseIds = getExerciseIdsForCircuit(circuitId).toMutableSet()
        circuit.exercises.forEachIndexed { exerciseIndex, exercise ->
            val exerciseId = upsertExercise(
                circuitId = circuitId,
                exercise = exercise,
                sortOrder = exerciseIndex,
            )
            remainingExerciseIds.remove(exerciseId)
        }
        if (remainingExerciseIds.isNotEmpty()) {
            deleteExercisesByIds(remainingExerciseIds.toList())
        }
        return circuitId
    }

    protected open suspend fun upsertExercise(
        circuitId: Long,
        exercise: ExerciseDraft,
        sortOrder: Int,
    ): Long {
        val entity = ExerciseTemplateEntity(
            id = exercise.id ?: 0,
            circuitId = circuitId,
            name = exercise.name.trim(),
            guidance = exercise.guidance.trim(),
            repMin = exercise.repMin,
            repMax = exercise.repMax,
            loadKind = exercise.loadKind,
            loadMin = exercise.loadMin,
            loadMax = exercise.loadMax,
            loadUnit = exercise.loadUnit,
            restTimeSeconds = exercise.restTimeSeconds,
            setCount = exercise.setCount,
            sortOrder = sortOrder,
        )
        return if (exercise.id == null) {
            insertExercise(entity.copy(id = 0))
        } else {
            updateExercise(entity)
            exercise.id
        }
    }
}
