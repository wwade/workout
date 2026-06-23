package dev.wwade.workout.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wwade.workout.domain.backup.DriveBackupSettings
import dev.wwade.workout.domain.backup.DriveBackupSettingsRepository
import dev.wwade.workout.domain.backup.ListDriveBackupSnapshotsUseCase
import dev.wwade.workout.domain.backup.RestoreDriveBackupUseCase
import dev.wwade.workout.domain.backup.SetDriveBackupEnabledUseCase
import dev.wwade.workout.domain.exporter.ExportWorkoutDataUseCase
import dev.wwade.workout.domain.importer.ImportWorkoutsUseCase
import dev.wwade.workout.domain.importer.WorkoutImportException
import dev.wwade.workout.domain.importer.WorkoutImportRequest
import dev.wwade.workout.domain.importer.WorkoutImportResult
import dev.wwade.workout.domain.importer.WorkoutImportSource
import dev.wwade.workout.domain.model.ActiveSessionSummary
import dev.wwade.workout.domain.model.WorkoutListItem
import dev.wwade.workout.domain.repository.SessionRepository
import dev.wwade.workout.domain.repository.WorkoutRepository
import dev.wwade.workout.domain.usecase.StartWorkoutUseCase
import dev.wwade.workout.ui.state.DriveBackupAuthorizationAction
import dev.wwade.workout.ui.state.DriveBackupDialog
import dev.wwade.workout.ui.state.WorkoutImportDialog
import dev.wwade.workout.ui.state.WorkoutListMessage
import dev.wwade.workout.ui.state.WorkoutListState
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutListViewModel(
    private val workoutRepository: WorkoutRepository,
    private val sessionRepository: SessionRepository,
    private val importWorkoutsUseCase: ImportWorkoutsUseCase = ImportWorkoutsUseCase(workoutRepository),
    private val exportWorkoutDataUseCase: ExportWorkoutDataUseCase,
    private val driveBackupSettingsRepository: DriveBackupSettingsRepository = DisabledDriveBackupSettingsRepository(),
    private val setDriveBackupEnabledUseCase: SetDriveBackupEnabledUseCase =
        SetDriveBackupEnabledUseCase(driveBackupSettingsRepository),
    private val listDriveBackupSnapshotsUseCase: ListDriveBackupSnapshotsUseCase? = null,
    private val restoreDriveBackupUseCase: RestoreDriveBackupUseCase? = null,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val startWorkoutUseCase = StartWorkoutUseCase(sessionRepository)
    private val importState = MutableStateFlow(WorkoutListImportState())
    private val driveState = MutableStateFlow(WorkoutListDriveState())
    private val selectedWorkoutIds = MutableStateFlow<Set<Long>>(emptySet())
    private val pendingDeleteCount = MutableStateFlow(0)
    private var currentDriveAccessToken: String? = null
    private val workouts = workoutRepository.observeWorkouts()
        .onEach { workouts ->
            val visibleWorkoutIds = workouts.map { it.id }.toSet()
            selectedWorkoutIds.update { selected -> selected.intersect(visibleWorkoutIds) }
        }

    private val homeState = combine(
        workouts,
        sessionRepository.observeActiveSession(),
        importState,
        driveBackupSettingsRepository.observeSettings(),
        driveState,
    ) { workouts, activeSession, importState, driveBackupSettings, driveState ->
        WorkoutListHomeState(
            workouts = workouts,
            activeSession = activeSession,
            importState = importState,
            driveBackupSettings = driveBackupSettings,
            driveState = driveState,
        )
    }

    val state: StateFlow<WorkoutListState> = combine(
        homeState,
        selectedWorkoutIds,
        pendingDeleteCount,
    ) { homeState, selectedIds, deleteCount ->
        WorkoutListState(
            workouts = homeState.workouts,
            activeSession = homeState.activeSession,
            selectedWorkoutIds = selectedIds,
            isSelectionMode = selectedIds.isNotEmpty(),
            pendingDeleteCount = deleteCount,
            isImporting = homeState.importState.isImporting,
            isExporting = homeState.importState.isExporting,
            importDialog = homeState.importState.importDialog,
            importUrl = homeState.importState.importUrl,
            message = homeState.importState.message,
            driveBackupSettings = homeState.driveBackupSettings,
            driveBackupDialog = homeState.driveState.dialog,
            driveBackupSnapshots = homeState.driveState.snapshots,
            pendingRestoreSnapshot = homeState.driveState.pendingRestoreSnapshot,
            isDriveBackupBusy = homeState.driveState.isBusy,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutListState(),
    )

    fun selectWorkout(workoutId: Long) {
        selectedWorkoutIds.update { it + workoutId }
    }

    fun toggleWorkoutSelection(workoutId: Long) {
        selectedWorkoutIds.update { selected ->
            if (workoutId in selected) selected - workoutId else selected + workoutId
        }
    }

    fun clearSelection() {
        selectedWorkoutIds.value = emptySet()
        pendingDeleteCount.value = 0
    }

    fun requestDeleteSelected() {
        val count = selectedWorkoutIds.value.size
        if (count > 0) {
            pendingDeleteCount.value = count
        }
    }

    fun cancelDelete() {
        pendingDeleteCount.value = 0
    }

    fun confirmDelete() {
        val workoutIds = selectedWorkoutIds.value
        if (workoutIds.isEmpty()) {
            pendingDeleteCount.value = 0
            return
        }

        viewModelScope.launch {
            workoutIds.forEach { workoutId ->
                workoutRepository.deleteWorkout(workoutId)
            }
            selectedWorkoutIds.value = emptySet()
            pendingDeleteCount.value = 0
        }
    }

    suspend fun startWorkout(workoutId: Long): Long {
        return startWorkoutUseCase(workoutId)
    }

    fun showImportOptions() {
        importState.update {
            it.copy(
                importDialog = WorkoutImportDialog.ChooseSource,
                message = null,
            )
        }
    }

    fun hideImportDialog() {
        importState.update { it.copy(importDialog = WorkoutImportDialog.None) }
    }

    fun showUrlImport() {
        importState.update {
            it.copy(
                importDialog = WorkoutImportDialog.UrlInput,
                message = null,
            )
        }
    }

    fun updateImportUrl(url: String) {
        importState.update { it.copy(importUrl = url) }
    }

    fun clearImportMessage() {
        importState.update { it.copy(message = null) }
    }

    fun showDriveBackupOptions() {
        driveState.update { it.copy(dialog = DriveBackupDialog.Options) }
        importState.update { it.copy(message = null) }
    }

    fun hideDriveBackupDialog() {
        driveState.update {
            it.copy(
                dialog = DriveBackupDialog.None,
                pendingRestoreSnapshot = null,
            )
        }
    }

    fun disableDriveBackup() {
        viewModelScope.launch {
            setDriveBackupEnabledUseCase(false)
            currentDriveAccessToken = null
            driveState.update { it.copy(dialog = DriveBackupDialog.None) }
            importState.update {
                it.copy(
                    message = WorkoutListMessage(
                        text = "Drive backup is disabled.",
                        isError = false,
                    ),
                )
            }
        }
    }

    fun onDriveAuthorizationFailed(message: String) {
        driveState.update { it.copy(isBusy = false) }
        importState.update {
            it.copy(
                message = WorkoutListMessage(
                    text = message.driveAuthorizationFailureMessage(),
                    isError = true,
                ),
            )
        }
    }

    fun onDriveAuthorizationToken(action: DriveBackupAuthorizationAction, accessToken: String) {
        currentDriveAccessToken = accessToken
        when (action) {
            DriveBackupAuthorizationAction.Enable -> enableDriveBackup()
            DriveBackupAuthorizationAction.ListSnapshots -> loadDriveBackupSnapshots(accessToken)
        }
    }

    private fun enableDriveBackup() {
        viewModelScope.launch {
            setDriveBackupEnabledUseCase(true)
            driveState.update { it.copy(dialog = DriveBackupDialog.None, isBusy = false) }
            importState.update {
                it.copy(
                    message = WorkoutListMessage(
                        text = "Drive backup is enabled. A backup will be saved when you complete a workout.",
                        isError = false,
                    ),
                )
            }
        }
    }

    private fun loadDriveBackupSnapshots(accessToken: String) {
        viewModelScope.launch {
            val listUseCase = listDriveBackupSnapshotsUseCase
            if (listUseCase == null) {
                onDriveAuthorizationFailed("Drive restore is not available.")
                return@launch
            }
            driveState.update { it.copy(isBusy = true, dialog = DriveBackupDialog.Snapshots) }
            runCatching {
                listUseCase(accessToken)
            }.onSuccess { snapshots ->
                driveState.update {
                    it.copy(
                        isBusy = false,
                        snapshots = snapshots,
                        dialog = DriveBackupDialog.Snapshots,
                    )
                }
                if (snapshots.isEmpty()) {
                    importState.update {
                        it.copy(
                            message = WorkoutListMessage(
                                text = "No Drive backups were found.",
                                isError = false,
                            ),
                        )
                    }
                }
            }.onFailure { error ->
                driveState.update { it.copy(isBusy = false, dialog = DriveBackupDialog.Options) }
                importState.update {
                    it.copy(
                        message = WorkoutListMessage(
                            text = error.message ?: "Unable to list Drive backups.",
                            isError = true,
                        ),
                    )
                }
            }
        }
    }

    fun requestRestoreDriveBackup(snapshotId: String) {
        val snapshot = driveState.value.snapshots.firstOrNull { it.id == snapshotId } ?: return
        driveState.update {
            it.copy(
                dialog = DriveBackupDialog.ConfirmRestore,
                pendingRestoreSnapshot = snapshot,
            )
        }
    }

    fun cancelRestoreDriveBackup() {
        driveState.update {
            it.copy(
                dialog = DriveBackupDialog.Snapshots,
                pendingRestoreSnapshot = null,
            )
        }
    }

    fun confirmRestoreDriveBackup() {
        val accessToken = currentDriveAccessToken
        val snapshot = driveState.value.pendingRestoreSnapshot
        val restoreUseCase = restoreDriveBackupUseCase
        if (accessToken.isNullOrBlank() || snapshot == null || restoreUseCase == null) {
            onDriveAuthorizationFailed("Drive restore needs Google Drive permission again.")
            return
        }

        viewModelScope.launch {
            driveState.update { it.copy(isBusy = true) }
            runCatching {
                restoreUseCase(accessToken, snapshot.id)
            }.onSuccess { result ->
                driveState.update {
                    it.copy(
                        isBusy = false,
                        dialog = DriveBackupDialog.None,
                        pendingRestoreSnapshot = null,
                    )
                }
                importState.update { it.copy(message = result.toMessage()) }
            }.onFailure { error ->
                driveState.update { it.copy(isBusy = false, dialog = DriveBackupDialog.Snapshots) }
                importState.update {
                    it.copy(
                        message = WorkoutListMessage(
                            text = error.importMessage(),
                            isError = true,
                        ),
                    )
                }
            }
        }
    }

    fun showImportError(message: String) {
        importState.update {
            it.copy(
                isImporting = false,
                importDialog = WorkoutImportDialog.None,
                message = WorkoutListMessage(
                    text = message,
                    isError = true,
                ),
            )
        }
    }

    suspend fun prepareExport(): WorkoutExportFile? {
        importState.update {
            it.copy(
                isExporting = true,
                message = null,
            )
        }

        return runCatching {
            val artifact = exportWorkoutDataUseCase()
            WorkoutExportFile(
                fileName = suggestedExportFileName(artifact.exportedAt),
                content = artifact.json,
            )
        }.onFailure { error ->
            importState.update {
                it.copy(
                    isExporting = false,
                    message = WorkoutListMessage(
                        text = error.message ?: "Unable to export workout data.",
                        isError = true,
                    ),
                )
            }
        }.getOrNull()
    }

    fun onExportSaved(fileName: String) {
        importState.update {
            it.copy(
                isExporting = false,
                message = WorkoutListMessage(
                    text = "Exported workout data to $fileName.",
                    isError = false,
                ),
            )
        }
    }

    fun onExportFailed(message: String) {
        importState.update {
            it.copy(
                isExporting = false,
                message = WorkoutListMessage(
                    text = message,
                    isError = true,
                ),
            )
        }
    }

    fun onExportCancelled() {
        importState.update { it.copy(isExporting = false) }
    }

    fun importFromJson(rawJson: String) {
        importWorkouts(WorkoutImportSource.RawJson(rawJson))
    }

    fun importFromUrl() {
        val url = importState.value.importUrl.trim()
        if (url.isBlank()) {
            importState.update {
                it.copy(
                    message = WorkoutListMessage(
                        text = "Enter a direct JSON or YAML URL to import.",
                        isError = true,
                    ),
                )
            }
            return
        }
        importWorkouts(WorkoutImportSource.Url(url))
    }

    private fun importWorkouts(source: WorkoutImportSource) {
        viewModelScope.launch {
            importState.update {
                it.copy(
                    isImporting = true,
                    importDialog = WorkoutImportDialog.None,
                    message = null,
                )
            }

            runCatching {
                importWorkoutsUseCase(WorkoutImportRequest(source))
            }.onSuccess { result ->
                importState.update {
                    it.copy(
                        isImporting = false,
                        message = result.toMessage(),
                        importUrl = if (source is WorkoutImportSource.Url) "" else it.importUrl,
                    )
                }
            }.onFailure { error ->
                importState.update {
                    it.copy(
                        isImporting = false,
                        message = WorkoutListMessage(
                            text = error.importMessage(),
                            isError = true,
                        ),
                    )
                }
            }
        }
    }

    private fun WorkoutImportResult.toMessage(): WorkoutListMessage {
        restoredCounts?.let { counts ->
            return WorkoutListMessage(
                text = "Restored ${counts.exerciseDefinitionCount} exercise${counts.exerciseDefinitionCount.pluralSuffix()}, " +
                    "${counts.workoutCount} workout${counts.workoutCount.pluralSuffix()}, and " +
                    "${counts.sessionCount} session${counts.sessionCount.pluralSuffix()}.",
                isError = false,
            )
        }

        val summary = when {
            importedCount == 0 -> "No workouts were imported."
            isSuccess -> "Imported $importedCount workout${importedCount.pluralSuffix()}."
            isPartialSuccess -> "Imported $importedCount workout${importedCount.pluralSuffix()}; $failedCount failed."
            else -> "No workouts were imported."
        }
        val details = workoutErrors.take(3).joinToString(separator = "\n")
        return WorkoutListMessage(
            text = if (details.isBlank()) summary else "$summary\n$details",
            isError = importedCount == 0,
        )
    }

    private fun suggestedExportFileName(exportedAt: Long): String {
        val date = EXPORT_FILE_DATE_FORMAT.format(
            Instant.ofEpochMilli(exportedAt).atZone(clock.zone),
        )
        return "workout-tracker-export-$date.json"
    }

    private fun Int.pluralSuffix(): String = if (this == 1) "" else "s"

    private fun Throwable.importMessage(): String {
        return when (this) {
            is WorkoutImportException -> message ?: "Unable to import workouts."
            else -> message ?: "Unable to import workouts."
        }
    }

    private fun String.driveAuthorizationFailureMessage(): String {
        return if (contains("UNREGISTERED_ON_API_CONSOLE")) {
            "Google Drive backup is not registered for this app build. " +
                "Create or update the Google Cloud Android OAuth client for this package name and signing SHA-1, " +
                "then confirm the Google Drive API is enabled."
        } else {
            this
        }
    }
}

private data class WorkoutListImportState(
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val importDialog: WorkoutImportDialog = WorkoutImportDialog.None,
    val importUrl: String = "",
    val message: WorkoutListMessage? = null,
)

private data class WorkoutListHomeState(
    val workouts: List<WorkoutListItem>,
    val activeSession: ActiveSessionSummary?,
    val importState: WorkoutListImportState,
    val driveBackupSettings: DriveBackupSettings,
    val driveState: WorkoutListDriveState,
)

private data class WorkoutListDriveState(
    val dialog: DriveBackupDialog = DriveBackupDialog.None,
    val snapshots: List<dev.wwade.workout.domain.backup.DriveBackupSnapshot> = emptyList(),
    val pendingRestoreSnapshot: dev.wwade.workout.domain.backup.DriveBackupSnapshot? = null,
    val isBusy: Boolean = false,
)

data class WorkoutExportFile(
    val fileName: String,
    val content: String,
)

private val EXPORT_FILE_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

private class DisabledDriveBackupSettingsRepository : DriveBackupSettingsRepository {
    private val settings = flowOf(DriveBackupSettings())

    override fun observeSettings() = settings

    override suspend fun getSettings(): DriveBackupSettings = DriveBackupSettings()

    override suspend fun setEnabled(enabled: Boolean) = Unit

    override suspend fun recordBackupSuccess(
        snapshot: dev.wwade.workout.domain.backup.DriveBackupSnapshot,
    ) = Unit

    override suspend fun recordBackupFailure(message: String) = Unit
}
