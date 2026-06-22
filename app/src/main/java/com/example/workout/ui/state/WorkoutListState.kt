package dev.wwade.workout.ui.state

import dev.wwade.workout.domain.model.ActiveSessionSummary
import dev.wwade.workout.domain.model.CompletedSessionListItem
import dev.wwade.workout.domain.model.WorkoutListItem
import dev.wwade.workout.domain.backup.DriveBackupSettings
import dev.wwade.workout.domain.backup.DriveBackupSnapshot

data class WorkoutListState(
    val workouts: List<WorkoutListItem> = emptyList(),
    val activeSession: ActiveSessionSummary? = null,
    val selectedWorkoutIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val pendingDeleteCount: Int = 0,
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val importDialog: WorkoutImportDialog = WorkoutImportDialog.None,
    val importUrl: String = "",
    val message: WorkoutListMessage? = null,
    val driveBackupSettings: DriveBackupSettings = DriveBackupSettings(),
    val driveBackupDialog: DriveBackupDialog = DriveBackupDialog.None,
    val driveBackupSnapshots: List<DriveBackupSnapshot> = emptyList(),
    val pendingRestoreSnapshot: DriveBackupSnapshot? = null,
    val isDriveBackupBusy: Boolean = false,
)

enum class WorkoutImportDialog {
    None,
    ChooseSource,
    UrlInput,
}

enum class DriveBackupDialog {
    None,
    Options,
    Snapshots,
    ConfirmRestore,
}

enum class DriveBackupAuthorizationAction {
    Enable,
    ListSnapshots,
}

data class WorkoutListMessage(
    val text: String,
    val isError: Boolean,
)

data class HistoryState(
    val sessions: List<CompletedSessionListItem> = emptyList(),
    val selectedSessionIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val pendingDeleteCount: Int = 0,
)
