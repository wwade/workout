package com.example.workout.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workout.domain.model.ExerciseProgressSnapshot
import com.example.workout.domain.model.SetEntryDraft
import com.example.workout.domain.repository.SessionRepository
import com.example.workout.domain.usecase.SessionProgressCalculator
import com.example.workout.ui.state.ActiveExerciseCardState
import com.example.workout.ui.state.ActiveSessionState
import com.example.workout.ui.state.SessionPositionOptionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class ActiveSessionViewModel(
    private val sessionRepository: SessionRepository,
    private val sessionId: Long,
) : ViewModel() {
    private val overrides = MutableStateFlow<Map<Long, EditableEntry>>(emptyMap())
    private val selectedPosition = MutableStateFlow<SessionProgressCalculator.CurrentPosition?>(null)
    private val transientUiState = MutableStateFlow(TransientUiState())

    val state: StateFlow<ActiveSessionState> = combine(
        sessionRepository.observeSessionDetail(sessionId),
        selectedPosition,
        overrides,
        transientUiState,
    ) { detail, selected, inputOverrides, transient ->
        if (detail == null) {
            ActiveSessionState(errorMessage = "Session not found.")
        } else {
            val selectedOrFallback = selected
                ?.takeIf { position -> SessionProgressCalculator.listPositions(detail).contains(position) }
            val progress = SessionProgressCalculator.buildSnapshot(
                detail = detail,
                selectedPosition = selectedOrFallback,
                prefillProvider = { exerciseTemplateId, setIndex ->
                    exerciseTemplateId?.let { id ->
                        sessionRepository.getLatestCompletedSetEntry(id, setIndex)
                    }
                },
            )
            val exerciseCards = progress.exercises.map { card ->
                val override = inputOverrides[card.exerciseSessionId]
                ActiveExerciseCardState(
                    exerciseSessionId = card.exerciseSessionId,
                    exerciseTemplateId = card.exerciseTemplateId,
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
                )
            }
            val selectablePositions = progress.availablePositions.filter { it.isSelectable }
            val currentSelectableIndex = selectablePositions.indexOfFirst {
                it.circuitIndex == progress.currentCircuitIndex && it.setIndex == progress.currentSetIndex
            }.coerceAtLeast(0)
            val hasChanges = exerciseCards.hasChangesComparedTo(progress.exercises)
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
                canSaveRound = hasChanges && !transient.isSaving,
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
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActiveSessionState(),
    )

    fun updateReps(exerciseSessionId: Long, value: String) {
        overrides.updateEntry(exerciseSessionId) { copy(reps = value) }
    }

    fun updateLoad(exerciseSessionId: Long, value: String) {
        overrides.updateEntry(exerciseSessionId) { copy(load = value) }
    }

    fun updateNotes(exerciseSessionId: Long, value: String) {
        overrides.updateEntry(exerciseSessionId) { copy(notes = value) }
    }

    fun updateSkipped(exerciseSessionId: Long, skipped: Boolean) {
        overrides.updateEntry(exerciseSessionId) { copy(skipped = skipped) }
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
        overrides.value = emptyMap()
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

        sessionRepository.saveRoundEntries(sessionId = sessionId, entries = entries)
        overrides.value = emptyMap()
        selectedPosition.value = null
        transientUiState.update { it.copy(isSaving = false, errorMessage = null) }
    }

    suspend fun abandonSession() {
        sessionRepository.abandonSession(sessionId)
    }

    private fun MutableStateFlow<Map<Long, EditableEntry>>.updateEntry(
        exerciseSessionId: Long,
        transform: EditableEntry.() -> EditableEntry,
    ) {
        update { current ->
            val entry = current[exerciseSessionId] ?: EditableEntry()
            current + (exerciseSessionId to entry.transform())
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

private fun formatLoad(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        value.toString()
    }
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
