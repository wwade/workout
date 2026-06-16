package dev.wwade.workout.domain.usecase

import dev.wwade.workout.domain.repository.SessionRepository

class StartWorkoutUseCase(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(workoutId: Long): Long {
        return sessionRepository.startWorkout(workoutId)
    }
}
