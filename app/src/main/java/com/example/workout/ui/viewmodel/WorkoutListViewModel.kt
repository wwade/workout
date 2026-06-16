package dev.wwade.workout.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import dev.wwade.workout.ui.state.WorkoutImportMessage
import dev.wwade.workout.ui.state.WorkoutListState
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
            importDialog = importState.importDialog,
            importUrl = importState.importUrl,
            importMessage = importState.importMessage,
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
                importMessage = null,
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
                importMessage = null,
            )
        }
    }

    fun updateImportUrl(url: String) {
        importState.update { it.copy(importUrl = url) }
    }

    fun clearImportMessage() {
        importState.update { it.copy(importMessage = null) }
    }

    fun showImportError(message: String) {
        importState.update {
            it.copy(
                isImporting = false,
                importDialog = WorkoutImportDialog.None,
                importMessage = WorkoutImportMessage(
                    text = message,
                    isError = true,
                ),
            )
        }
    }

    fun importFromJson(rawJson: String) {
        importWorkouts(WorkoutImportSource.RawJson(rawJson))
    }

    fun importFromUrl() {
        val url = importState.value.importUrl.trim()
        if (url.isBlank()) {
            importState.update {
                it.copy(
                    importMessage = WorkoutImportMessage(
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
                    importMessage = null,
                )
            }

            runCatching {
                importWorkoutsUseCase(WorkoutImportRequest(source))
            }.onSuccess { result ->
                importState.update {
                    it.copy(
                        isImporting = false,
                        importMessage = result.toMessage(),
                        importUrl = if (source is WorkoutImportSource.Url) "" else it.importUrl,
                    )
                }
            }.onFailure { error ->
                importState.update {
                    it.copy(
                        isImporting = false,
                        importMessage = WorkoutImportMessage(
                            text = error.importMessage(),
                            isError = true,
                        ),
                    )
                }
            }
        }
    }

    private fun WorkoutImportResult.toMessage(): WorkoutImportMessage {
        val summary = when {
            importedCount == 0 -> "No workouts were imported."
            isSuccess -> "Imported $importedCount workout${importedCount.pluralSuffix()}."
            isPartialSuccess -> "Imported $importedCount workout${importedCount.pluralSuffix()}; $failedCount failed."
            else -> "No workouts were imported."
        }
        val details = workoutErrors.take(3).joinToString(separator = "\n")
        return WorkoutImportMessage(
            text = if (details.isBlank()) summary else "$summary\n$details",
            isError = importedCount == 0,
        )
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
    val importDialog: WorkoutImportDialog = WorkoutImportDialog.None,
    val importUrl: String = "",
    val importMessage: WorkoutImportMessage? = null,
)
