package com.example.workout.ui.state

import com.example.workout.domain.model.CompletedSessionListItem
import com.example.workout.domain.model.WorkoutListItem

data class WorkoutListState(
    val workouts: List<WorkoutListItem> = emptyList(),
    val activeSessionId: Long? = null,
)

data class HistoryState(
    val sessions: List<CompletedSessionListItem> = emptyList(),
)
