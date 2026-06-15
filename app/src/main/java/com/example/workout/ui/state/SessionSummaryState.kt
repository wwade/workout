package com.example.workout.ui.state

import com.example.workout.domain.model.LoadKind
import com.example.workout.domain.model.LoadUnit

data class SessionSummaryState(
    val sessionId: Long = 0,
    val workoutName: String = "",
    val startedAt: Long = 0,
    val completedAt: Long? = null,
    val circuits: List<SessionSummaryCircuitState> = emptyList(),
)

data class SessionSummaryCircuitState(
    val name: String,
    val setCount: Int,
    val exercises: List<SessionSummaryExerciseState>,
)

data class SessionSummaryExerciseState(
    val name: String,
    val guidance: String,
    val loadKind: LoadKind,
    val loadUnit: LoadUnit,
    val restTimeSeconds: Int,
    val sets: List<SessionSummarySetState>,
)

data class SessionSummarySetState(
    val setNumber: Int,
    val reps: Int?,
    val load: Double?,
    val notes: String,
    val skipped: Boolean,
)
