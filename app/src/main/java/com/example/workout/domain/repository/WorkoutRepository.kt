package com.example.workout.domain.repository

import com.example.workout.domain.model.WorkoutDraft
import com.example.workout.domain.model.WorkoutListItem
import com.example.workout.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    fun observeWorkouts(): Flow<List<WorkoutListItem>>
    suspend fun getWorkout(workoutId: Long): WorkoutTemplate?
    suspend fun saveWorkout(workout: WorkoutDraft): Long
    suspend fun deleteWorkout(workoutId: Long)
}
