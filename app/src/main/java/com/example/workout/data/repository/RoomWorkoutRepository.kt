package com.example.workout.data.repository

import com.example.workout.data.db.WorkoutTemplateDao
import com.example.workout.domain.model.WorkoutDraft
import com.example.workout.domain.model.WorkoutListItem
import com.example.workout.domain.model.WorkoutTemplate
import com.example.workout.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomWorkoutRepository(
    private val workoutTemplateDao: WorkoutTemplateDao,
) : WorkoutRepository {
    override fun observeWorkouts(): Flow<List<WorkoutListItem>> {
        return workoutTemplateDao.observeWorkoutTemplates().map { workouts ->
            workouts.map { workout ->
                val graph = workoutTemplateDao.getWorkoutTemplateGraph(workout.id)
                val circuitCount = graph?.circuits?.size ?: 0
                val exerciseCount = graph?.circuits?.sumOf { it.exercises.size } ?: 0
                workout.toListItem(
                    circuitCount = circuitCount,
                    exerciseCount = exerciseCount,
                )
            }
        }
    }

    override suspend fun getWorkout(workoutId: Long): WorkoutTemplate? {
        return workoutTemplateDao.getWorkoutTemplateGraph(workoutId)?.toDomain()
    }

    override suspend fun saveWorkout(workout: WorkoutDraft): Long {
        return workoutTemplateDao.upsertWorkoutGraph(workout)
    }

    override suspend fun deleteWorkout(workoutId: Long) {
        workoutTemplateDao.deleteWorkoutById(workoutId)
    }
}
