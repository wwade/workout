package dev.wwade.workout.ui.state

import dev.wwade.workout.domain.model.WorkoutDraft
import dev.wwade.workout.domain.usecase.WorkoutValidationResult

data class WorkoutEditorState(
    val draft: WorkoutDraft = WorkoutDraft(),
    val validation: WorkoutValidationResult = WorkoutValidationResult(),
    val isSaving: Boolean = false,
    val saveError: String? = null,
)
