package dev.wwade.workout.ui.state

import dev.wwade.workout.domain.model.CompletedSessionListItem
import dev.wwade.workout.domain.model.WorkoutListItem

data class WorkoutListState(
    val workouts: List<WorkoutListItem> = emptyList(),
    val activeSessionId: Long? = null,
    val isImporting: Boolean = false,
    val importDialog: WorkoutImportDialog = WorkoutImportDialog.None,
    val importUrl: String = "",
    val importMessage: WorkoutImportMessage? = null,
)

enum class WorkoutImportDialog {
    None,
    ChooseSource,
    UrlInput,
}

data class WorkoutImportMessage(
    val text: String,
    val isError: Boolean,
)

data class HistoryState(
    val sessions: List<CompletedSessionListItem> = emptyList(),
)
