package dev.wwade.workout.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wwade.workout.domain.exporter.ExportWorkoutDataUseCase
import dev.wwade.workout.domain.importer.ImportWorkoutsUseCase
import dev.wwade.workout.domain.importer.WorkoutImportException
import dev.wwade.workout.domain.importer.WorkoutImportRequest
import dev.wwade.workout.domain.importer.WorkoutImportResult
import dev.wwade.workout.domain.importer.WorkoutImportSource
import dev.wwade.workout.domain.model.WorkoutListItem
import dev.wwade.workout.domain.repository.SessionRepository
import dev.wwade.workout.domain.repository.WorkoutRepository
import dev.wwade.workout.domain.usecase.StartWorkoutUseCase
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutListViewModel(
    private val workoutRepository: WorkoutRepository,
    private val sessionRepository: SessionRepository,
    private val importWorkoutsUseCase: ImportWorkoutsUseCase = ImportWorkoutsUseCase(workoutRepository),
    private val exportWorkoutDataUseCase: ExportWorkoutDataUseCase,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val startWorkoutUseCase = StartWorkoutUseCase(sessionRepository)
    private val importState = MutableStateFlow(WorkoutListImportState())

    val state: StateFlow<WorkoutListState> = combine(
        workoutRepository.observeWorkouts(),
        sessionRepository.observeActiveSessionId(),
        importState,
    ) { workouts, activeSessionId, importState ->
        WorkoutListState(
            workouts = workouts,
            activeSessionId = activeSessionId,
            isImporting = importState.isImporting,
            isExporting = importState.isExporting,
            importDialog = importState.importDialog,
            importUrl = importState.importUrl,
            message = importState.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutListState(),
    )

    fun deleteWorkout(workout: WorkoutListItem) {
        viewModelScope.launch {
            workoutRepository.deleteWorkout(workout.id)
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
}

private data class WorkoutListImportState(
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val importDialog: WorkoutImportDialog = WorkoutImportDialog.None,
    val importUrl: String = "",
    val message: WorkoutListMessage? = null,
)

data class WorkoutExportFile(
    val fileName: String,
    val content: String,
)

private val EXPORT_FILE_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
