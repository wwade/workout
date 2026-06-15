package com.example.workout.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workout.domain.model.WorkoutListItem
import com.example.workout.domain.repository.SessionRepository
import com.example.workout.domain.repository.WorkoutRepository
import com.example.workout.domain.usecase.StartWorkoutUseCase
import com.example.workout.ui.state.WorkoutListState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutListViewModel(
    private val workoutRepository: WorkoutRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val startWorkoutUseCase = StartWorkoutUseCase(sessionRepository)

    val state: StateFlow<WorkoutListState> = combine(
        workoutRepository.observeWorkouts(),
        sessionRepository.observeActiveSessionId(),
    ) { workouts, activeSessionId ->
        WorkoutListState(
            workouts = workouts,
            activeSessionId = activeSessionId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutListState(),
    )

    fun deleteWorkout(workout: WorkoutListItem) {
        viewModelScope.launch {
            workoutRepository.deleteWorkout(workout.id)
        }
    }

    suspend fun startWorkout(workoutId: Long): Long {
        return startWorkoutUseCase(workoutId)
    }
}
