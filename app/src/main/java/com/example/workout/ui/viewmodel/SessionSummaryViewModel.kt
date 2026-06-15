package com.example.workout.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workout.domain.repository.SessionRepository
import com.example.workout.ui.state.SessionSummaryCircuitState
import com.example.workout.ui.state.SessionSummaryExerciseState
import com.example.workout.ui.state.SessionSummarySetState
import com.example.workout.ui.state.SessionSummaryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionSummaryViewModel(
    private val sessionRepository: SessionRepository,
    private val sessionId: Long,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionSummaryState())
    val state: StateFlow<SessionSummaryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.getSessionDetail(sessionId)?.let { detail ->
                _state.value = SessionSummaryState(
                    sessionId = detail.sessionId,
                    workoutName = detail.workoutName,
                    startedAt = detail.startedAt,
                    completedAt = detail.completedAt,
                    circuits = detail.circuits.map { circuit ->
                        SessionSummaryCircuitState(
                            name = circuit.name,
                            setCount = circuit.setCount,
                            exercises = circuit.exercises.map { exercise ->
                                SessionSummaryExerciseState(
                                    name = exercise.name,
                                    guidance = exercise.guidance,
                                    loadKind = exercise.loadKind,
                                    loadUnit = exercise.loadUnit,
                                    restTimeSeconds = exercise.restTimeSeconds,
                                    sets = exercise.sets.map { set ->
                                        SessionSummarySetState(
                                            setNumber = set.setIndex + 1,
                                            reps = set.repsActual,
                                            load = set.loadActual,
                                            notes = set.notes,
                                            skipped = set.skipped,
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }
        }
    }
}
