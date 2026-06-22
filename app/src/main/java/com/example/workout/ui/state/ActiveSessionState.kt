package dev.wwade.workout.ui.state

import dev.wwade.workout.domain.model.LoadUnit

data class ActiveSessionState(
    val sessionId: Long = 0,
    val workoutName: String = "",
    val currentCircuitIndex: Int = 0,
    val currentCircuitName: String = "",
    val currentSetIndex: Int = 0,
    val totalSetsInCircuit: Int = 0,
    val isLastRound: Boolean = false,
    val isLastCircuit: Boolean = false,
    val isCompleted: Boolean = false,
    val isSaving: Boolean = false,
    val canSaveRound: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val errorMessage: String? = null,
    val positionOptions: List<SessionPositionOptionState> = emptyList(),
    val exerciseCards: List<ActiveExerciseCardState> = emptyList(),
    val historyDialog: ActiveExerciseHistoryDialogState? = null,
)

data class SessionPositionOptionState(
    val circuitIndex: Int,
    val setIndex: Int,
    val label: String,
    val statusLabel: String,
    val isSelectable: Boolean,
)

data class ActiveExerciseCardState(
    val exerciseSessionId: Long,
    val exerciseTemplateId: Long?,
    val exerciseDefinitionId: Long?,
    val exerciseName: String,
    val guidance: String,
    val repRangeLabel: String,
    val loadRangeLabel: String,
    val loadUnit: LoadUnit,
    val restTimeSeconds: Int,
    val repsInput: String,
    val loadInput: String,
    val notesInput: String,
    val skipped: Boolean,
    val previousWorkoutHistory: List<ActiveExerciseHistoryRowState> = emptyList(),
    val fullHistory: List<ActiveExerciseHistoryRowState> = emptyList(),
)

data class ActiveExerciseHistoryRowState(
    val workoutSessionId: Long,
    val workoutName: String,
    val completedAtLabel: String,
    val setLabel: String,
    val resultLabel: String,
    val notes: String,
)

data class ActiveExerciseHistoryDialogState(
    val exerciseSessionId: Long,
    val exerciseName: String,
    val rows: List<ActiveExerciseHistoryRowState>,
)
