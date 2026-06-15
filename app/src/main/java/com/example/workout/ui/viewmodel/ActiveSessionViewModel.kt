package com.example.workout.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workout.domain.model.SetEntryDraft
import com.example.workout.domain.repository.SessionRepository
import com.example.workout.domain.usecase.GetSessionProgressUseCase
import com.example.workout.ui.state.ActiveExerciseCardState
import com.example.workout.ui.state.ActiveSessionState
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
    private val progressUseCase = GetSessionProgressUseCase(sessionRepository)
    private val overrides = MutableStateFlow<Map<Long, EditableEntry>>(emptyMap())
    private val transientUiState = MutableStateFlow(TransientUiState())

    val state: StateFlow<ActiveSessionState> = combine(
        progressUseCase(sessionId),
        overrides,
        transientUiState,
    ) { progress, inputOverrides, transient ->
        if (progress == null) {
            ActiveSessionState(errorMessage = "Session not found.")
        } else {
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
                errorMessage = transient.errorMessage,
                exerciseCards = progress.exercises.map { card ->
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
                        suggestedRepsText = card.suggestedReps?.toString().orEmpty(),
                        suggestedLoadText = card.suggestedLoad?.let(::formatLoad).orEmpty(),
                        repsInput = override?.reps.orEmpty(),
                        loadInput = override?.load.orEmpty(),
                        notesInput = override?.notes ?: card.suggestedNotes,
                        skipped = override?.skipped ?: card.suggestedSkipped,
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

    suspend fun saveRound() {
        val current = state.value
        transientUiState.update { it.copy(isSaving = true, errorMessage = null) }
        val entries = current.exerciseCards.mapNotNull { card ->
            val reps = card.repsInput.ifBlank { card.suggestedRepsText }.toIntOrNull()
            val load = card.loadInput.ifBlank { card.suggestedLoadText }.toDoubleOrNull()
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
        val reps: String = "",
        val load: String = "",
        val notes: String = "",
        val skipped: Boolean = false,
    )

    private data class TransientUiState(
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
    )
}

private fun formatLoad(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        value.toString()
    }
}
