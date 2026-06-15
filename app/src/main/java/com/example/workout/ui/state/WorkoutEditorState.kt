package com.example.workout.ui.state

import com.example.workout.domain.model.WorkoutDraft
import com.example.workout.domain.usecase.WorkoutValidationResult

data class WorkoutEditorState(
    val draft: WorkoutDraft = WorkoutDraft(),
    val validation: WorkoutValidationResult = WorkoutValidationResult(),
    val isSaving: Boolean = false,
    val saveError: String? = null,
)
