package dev.wwade.workout.ui.state

import dev.wwade.workout.domain.model.CompletedSessionListItem
import dev.wwade.workout.domain.model.WorkoutListItem

data class WorkoutListState(
    val workouts: List<WorkoutListItem> = emptyList(),
    val activeSessionId: Long? = null,
)

data class HistoryState(
    val sessions: List<CompletedSessionListItem> = emptyList(),
)
