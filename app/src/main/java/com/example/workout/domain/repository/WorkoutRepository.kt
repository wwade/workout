package dev.wwade.workout.domain.repository

import dev.wwade.workout.domain.model.WorkoutDraft
import dev.wwade.workout.domain.model.WorkoutListItem
import dev.wwade.workout.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    fun observeWorkouts(): Flow<List<WorkoutListItem>>
    suspend fun getWorkout(workoutId: Long): WorkoutTemplate?
    suspend fun saveWorkout(workout: WorkoutDraft): Long
    suspend fun deleteWorkout(workoutId: Long)
}
