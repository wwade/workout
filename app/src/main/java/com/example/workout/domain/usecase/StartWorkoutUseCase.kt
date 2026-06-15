package com.example.workout.domain.usecase

import com.example.workout.domain.repository.SessionRepository

class StartWorkoutUseCase(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(workoutId: Long): Long {
        return sessionRepository.startWorkout(workoutId)
    }
}
