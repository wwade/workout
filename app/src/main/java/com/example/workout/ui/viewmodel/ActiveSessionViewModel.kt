package dev.wwade.workout.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wwade.workout.domain.model.ExerciseSetHistoryItem
import dev.wwade.workout.domain.model.ExerciseProgressSnapshot
import dev.wwade.workout.domain.model.LoadUnit
import dev.wwade.workout.domain.model.SetEntryDraft
import dev.wwade.workout.domain.backup.BackupNowAfterWorkoutCompletionUseCase
import dev.wwade.workout.domain.repository.SessionRepository
import dev.wwade.workout.domain.usecase.SessionProgressCalculator
import dev.wwade.workout.ui.state.ActiveExerciseCardState
import dev.wwade.workout.ui.state.ActiveExerciseHistoryDialogState
import dev.wwade.workout.ui.state.ActiveExerciseHistoryRowState
import dev.wwade.workout.ui.state.ActiveSessionState
import dev.wwade.workout.ui.state.SessionPositionOptionState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ActiveSessionViewModel(
    private val sessionRepository: SessionRepository,
    private val sessionId: Long,
    private val backupNowAfterWorkoutCompletionUseCase: BackupNowAfterWorkoutCompletionUseCase? = null,
) : ViewModel() {
    private val overrides = MutableStateFlow<Map<RoundKey, Map<Long, EditableEntry>>>(emptyMap())
    private val selectedPosition = MutableStateFlow<SessionProgressCalculator.CurrentPosition?>(null)
    private val selectedHistoryExerciseSessionId = MutableStateFlow<Long?>(null)
    private val transientUiState = MutableStateFlow(TransientUiState())

    val state: StateFlow<ActiveSessionState> = combine(
        sessionRepository.observeSessionDetail(sessionId),
        selectedPosition,
        overrides,
        selectedHistoryExerciseSessionId,
        transientUiState,
    ) { detail, selected, inputOverrides, historyExerciseSessionId, transient ->
        if (detail == null) {
            ActiveSessionState(errorMessage = "Session not found.")
        } else {
            val selectedOrFallback = selected
                ?.takeIf { position -> SessionProgressCalculator.listPositions(detail).contains(position) }
            val progress = SessionProgressCalculator.buildSnapshot(
                detail = detail,
                selectedPosition = selectedOrFallback,
                prefillProvider = { exerciseDefinitionId, setIndex ->
                    exerciseDefinitionId?.let { id ->
                        sessionRepository.getLatestCompletedSetEntry(id, setIndex)
                    }
                },
            )
            val activeRoundKey = RoundKey(
                circuitIndex = progress.currentCircuitIndex,
                setIndex = progress.currentSetIndex,
            )
            val roundOverrides = inputOverrides[activeRoundKey].orEmpty()
            val exerciseCards = progress.exercises.map { card ->
                val override = roundOverrides[card.exerciseSessionId]
                val previousWorkoutHistory = card.exerciseDefinitionId?.let { exerciseDefinitionId ->
                    sessionRepository.getPreviousWorkoutSetEntries(exerciseDefinitionId)
                        .map { it.toHistoryRow(card.loadUnit) }
                }.orEmpty()
                val fullHistory = card.exerciseDefinitionId?.let { exerciseDefinitionId ->
                    sessionRepository.getRecentCompletedSetEntries(
                        exerciseDefinitionId = exerciseDefinitionId,
                        limit = MaxHistoryRows,
                    ).map { it.toHistoryRow(card.loadUnit) }
                }.orEmpty()
                ActiveExerciseCardState(
                    exerciseSessionId = card.exerciseSessionId,
                    exerciseTemplateId = card.exerciseTemplateId,
                    exerciseDefinitionId = card.exerciseDefinitionId,
                    exerciseName = card.exerciseName,
                    guidance = card.guidance,
                    repRangeLabel = card.repRangeLabel,
                    loadRangeLabel = card.loadRangeLabel,
                    loadUnit = card.loadUnit,
                    restTimeSeconds = card.restTimeSeconds,
                    repsInput = override?.reps ?: card.suggestedReps?.toString().orEmpty(),
                    loadInput = override?.load ?: card.suggestedLoad?.let(::formatLoad).orEmpty(),
                    notesInput = override?.notes ?: card.suggestedNotes,
                    skipped = override?.skipped ?: card.suggestedSkipped,
                    previousWorkoutHistory = previousWorkoutHistory,
                    fullHistory = fullHistory,
                )
            }
            val currentPosition = progress.availablePositions.firstOrNull {
                it.circuitIndex == progress.currentCircuitIndex && it.setIndex == progress.currentSetIndex
            }
            val selectablePositions = progress.availablePositions.filter { it.isSelectable }
            val currentSelectableIndex = selectablePositions.indexOfFirst {
                it.circuitIndex == progress.currentCircuitIndex && it.setIndex == progress.currentSetIndex
            }.coerceAtLeast(0)
            val hasChanges = exerciseCards.hasChangesComparedTo(progress.exercises)
            val canSaveRound = when {
                transient.isSaving -> false
                currentPosition?.isSaved == true -> hasChanges
                else -> true
            }
            ActiveSessionState(
                sessionId = progress.sessionId,
                workoutName = progress.workoutName,
                currentCircuitIndex = progress.currentCircuitIndex,
                currentCircuitName = progress.currentCircuitName,
                currentSetIndex = progress.currentSetIndex,
                totalSetsInCircuit = progress.totalSetsInCircuit,
                isLastRound = progress.isLastRound,
                isLastCircuit = progress.isLastCircuit,
                isCompleted = progress.isCompleted,
                isSaving = transient.isSaving,
                canSaveRound = canSaveRound,
                canGoBack = currentSelectableIndex > 0,
                canGoForward = currentSelectableIndex >= 0 && currentSelectableIndex < selectablePositions.lastIndex,
                errorMessage = transient.errorMessage,
                positionOptions = progress.availablePositions.map { position ->
                    SessionPositionOptionState(
                        circuitIndex = position.circuitIndex,
                        setIndex = position.setIndex,
                        label = "${position.circuitName} - Set ${position.setIndex + 1}",
                        statusLabel = when {
                            position.isSaved -> "Saved"
                            position.isCurrentTarget -> "Current"
                            else -> "Upcoming"
                        },
                        isSelectable = position.isSelectable,
                    )
                },
                exerciseCards = exerciseCards,
                historyDialog = exerciseCards.firstOrNull {
                    it.exerciseSessionId == historyExerciseSessionId
                }?.let { exercise ->
                    ActiveExerciseHistoryDialogState(
                        exerciseSessionId = exercise.exerciseSessionId,
                        exerciseName = exercise.exerciseName,
                        rows = exercise.fullHistory,
                    )
                },
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActiveSessionState(),
    )

    fun updateReps(exerciseSessionId: Long, value: String) {
        overrides.updateEntry(state.value.currentRoundKey(), exerciseSessionId) { copy(reps = value) }
    }

    fun updateLoad(exerciseSessionId: Long, value: String) {
        overrides.updateEntry(state.value.currentRoundKey(), exerciseSessionId) { copy(load = value) }
    }

    fun updateNotes(exerciseSessionId: Long, value: String) {
        overrides.updateEntry(state.value.currentRoundKey(), exerciseSessionId) { copy(notes = value) }
    }

    fun updateSkipped(exerciseSessionId: Long, skipped: Boolean) {
        overrides.updateEntry(state.value.currentRoundKey(), exerciseSessionId) { copy(skipped = skipped) }
    }

    fun showExerciseHistory(exerciseSessionId: Long) {
        selectedHistoryExerciseSessionId.value = exerciseSessionId
    }

    fun dismissExerciseHistory() {
        selectedHistoryExerciseSessionId.value = null
    }

    fun goToPreviousRound() {
        moveSelection(offset = -1)
    }

    fun goToNextRound() {
        moveSelection(offset = 1)
    }

    fun selectRound(circuitIndex: Int, setIndex: Int) {
        val current = state.value
        val target = current.positionOptions.firstOrNull {
            it.circuitIndex == circuitIndex && it.setIndex == setIndex
        } ?: return
        if (!target.isSelectable) return
        if (current.currentCircuitIndex == circuitIndex && current.currentSetIndex == setIndex) return
        selectedPosition.value = SessionProgressCalculator.CurrentPosition(
            circuitIndex = circuitIndex,
            setIndex = setIndex,
        )
        transientUiState.update { it.copy(errorMessage = null) }
    }

    suspend fun saveRound() {
        val current = state.value
        if (!current.canSaveRound) {
            return
        }
        transientUiState.update { it.copy(isSaving = true, errorMessage = null) }
        val entries = current.exerciseCards.mapNotNull { card ->
            val reps = card.repsInput.toIntOrNull()
            val load = card.loadInput.toDoubleOrNull()
            if (!card.skipped && (reps == null || load == null)) {
                transientUiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Enter reps and load for every exercise, or mark it skipped.",
                    )
                }
                return
            }
            SetEntryDraft(
                exerciseSessionId = card.exerciseSessionId,
                setIndex = current.currentSetIndex,
                repsActual = if (card.skipped) null else reps,
                loadActual = if (card.skipped) null else load,
                notes = card.notesInput,
                skipped = card.skipped,
            )
        }

        val newlyCompleted = sessionRepository.saveRoundEntries(sessionId = sessionId, entries = entries)
        val savedRoundKey = current.currentRoundKey()
        overrides.update { it - savedRoundKey }
        selectedPosition.value = null
        transientUiState.update { it.copy(isSaving = false, errorMessage = null) }
        if (newlyCompleted) {
            viewModelScope.launch {
                backupNowAfterWorkoutCompletionUseCase?.invoke()
            }
        }
    }

    suspend fun abandonSession() {
        sessionRepository.abandonSession(sessionId)
    }

    private fun MutableStateFlow<Map<RoundKey, Map<Long, EditableEntry>>>.updateEntry(
        roundKey: RoundKey,
        exerciseSessionId: Long,
        transform: EditableEntry.() -> EditableEntry,
    ) {
        update { current ->
            val roundEntries = current[roundKey].orEmpty()
            val entry = roundEntries[exerciseSessionId] ?: EditableEntry()
            current + (roundKey to (roundEntries + (exerciseSessionId to entry.transform())))
        }
    }

    private data class EditableEntry(
        val reps: String? = null,
        val load: String? = null,
        val notes: String? = null,
        val skipped: Boolean? = null,
    )

    private data class TransientUiState(
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
    )

    private fun moveSelection(offset: Int) {
        val positions = state.value.positionOptions.filter { it.isSelectable }
        if (positions.isEmpty()) return
        val currentIndex = positions.indexOfFirst {
            it.circuitIndex == state.value.currentCircuitIndex && it.setIndex == state.value.currentSetIndex
        }.coerceAtLeast(0)
        val target = positions.getOrNull(currentIndex + offset) ?: return
        selectRound(target.circuitIndex, target.setIndex)
    }
}

private data class RoundKey(
    val circuitIndex: Int,
    val setIndex: Int,
)

private fun ActiveSessionState.currentRoundKey(): RoundKey {
    return RoundKey(
        circuitIndex = currentCircuitIndex,
        setIndex = currentSetIndex,
    )
}

private fun formatLoad(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        value.toString()
    }
}

private fun ExerciseSetHistoryItem.toHistoryRow(loadUnit: LoadUnit): ActiveExerciseHistoryRowState {
    return ActiveExerciseHistoryRowState(
        workoutSessionId = workoutSessionId,
        workoutName = workoutName,
        completedAtLabel = HistoryDateFormatter.format(
            Instant.ofEpochMilli(completedAt).atZone(ZoneId.systemDefault()),
        ),
        setLabel = "Set ${setIndex + 1}",
        resultLabel = if (skipped) {
            "Skipped"
        } else {
            listOfNotNull(
                repsActual?.let { "$it reps" },
                loadActual?.let { "${formatLoad(it)} ${loadUnit.name.lowercase()}" },
            ).joinToString(" - ").ifBlank { "No values" }
        },
        notes = notes,
    )
}

private fun List<ActiveExerciseCardState>.hasChangesComparedTo(
    snapshots: List<ExerciseProgressSnapshot>,
): Boolean {
    if (size != snapshots.size) return true
    return zip(snapshots).any { (card, snapshot) ->
        card.repsInput != snapshot.suggestedReps?.toString().orEmpty() ||
            card.loadInput != snapshot.suggestedLoad?.let(::formatLoad).orEmpty() ||
            card.notesInput != snapshot.suggestedNotes ||
            card.skipped != snapshot.suggestedSkipped
    }
}

private const val MaxHistoryRows = 24

private val HistoryDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
