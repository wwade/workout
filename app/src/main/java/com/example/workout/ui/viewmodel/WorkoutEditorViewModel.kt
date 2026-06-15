package com.example.workout.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workout.BuildConfig
import com.example.workout.domain.model.CircuitDraft
import com.example.workout.domain.model.DefaultWorkoutDrafts
import com.example.workout.domain.model.ExerciseDraft
import com.example.workout.domain.model.WorkoutDraft
import com.example.workout.domain.repository.WorkoutRepository
import com.example.workout.domain.usecase.ValidateWorkoutDraftUseCase
import com.example.workout.ui.state.WorkoutEditorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutEditorViewModel(
    private val workoutRepository: WorkoutRepository,
    private val workoutId: Long?,
) : ViewModel() {
    private val validator = ValidateWorkoutDraftUseCase()
    private val _state = MutableStateFlow(
        WorkoutEditorState(
            draft = defaultWorkoutDraft(),
        ),
    )
    val state: StateFlow<WorkoutEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (workoutId != null) {
                workoutRepository.getWorkout(workoutId)?.let { workout ->
                    updateDraft(
                        WorkoutDraft(
                            id = workout.id,
                            name = workout.name,
                            circuits = workout.circuits.map { circuit ->
                                CircuitDraft(
                                    id = circuit.id,
                                    name = circuit.name,
                                    exercises = circuit.exercises.map { exercise ->
                                        ExerciseDraft(
                                            id = exercise.id,
                                            name = exercise.name,
                                            guidance = exercise.guidance,
                                            repMin = exercise.repMin,
                                            repMax = exercise.repMax,
                                            loadKind = exercise.loadKind,
                                            loadMin = exercise.loadMin,
                                            loadMax = exercise.loadMax,
                                            loadUnit = exercise.loadUnit,
                                            restTimeSeconds = exercise.restTimeSeconds,
                                            setCount = exercise.setCount,
                                        )
                                    },
                                )
                            },
                        ),
                    )
                }
            } else {
                validate()
            }
        }
    }

    fun updateWorkoutName(name: String) {
        updateDraft(state.value.draft.copy(name = name))
    }

    fun upsertCircuit(index: Int?, name: String) {
        val circuits = state.value.draft.circuits.toMutableList()
        if (index == null) {
            circuits += CircuitDraft(
                name = name,
                exercises = listOf(ExerciseDraft()),
            )
        } else {
            circuits[index] = circuits[index].copy(name = name)
        }
        updateDraft(state.value.draft.copy(circuits = circuits))
    }

    fun removeCircuit(index: Int) {
        val circuits = state.value.draft.circuits.toMutableList()
        circuits.removeAt(index)
        updateDraft(state.value.draft.copy(circuits = circuits))
    }

    fun moveCircuit(index: Int, direction: Int) {
        val targetIndex = index + direction
        val circuits = state.value.draft.circuits.toMutableList()
        if (targetIndex !in circuits.indices) return
        val item = circuits.removeAt(index)
        circuits.add(targetIndex, item)
        updateDraft(state.value.draft.copy(circuits = circuits))
    }

    fun upsertExercise(circuitIndex: Int, exerciseIndex: Int?, exercise: ExerciseDraft) {
        val circuits = state.value.draft.circuits.toMutableList()
        val exercises = circuits[circuitIndex].exercises.toMutableList()
        if (exerciseIndex == null) {
            exercises += exercise
        } else {
            exercises[exerciseIndex] = exercise
        }
        circuits[circuitIndex] = circuits[circuitIndex].copy(exercises = exercises)
        updateDraft(state.value.draft.copy(circuits = circuits))
    }

    fun removeExercise(circuitIndex: Int, exerciseIndex: Int) {
        val circuits = state.value.draft.circuits.toMutableList()
        val exercises = circuits[circuitIndex].exercises.toMutableList()
        exercises.removeAt(exerciseIndex)
        circuits[circuitIndex] = circuits[circuitIndex].copy(exercises = exercises)
        updateDraft(state.value.draft.copy(circuits = circuits))
    }

    fun moveExercise(circuitIndex: Int, exerciseIndex: Int, direction: Int) {
        val circuits = state.value.draft.circuits.toMutableList()
        val exercises = circuits[circuitIndex].exercises.toMutableList()
        val targetIndex = exerciseIndex + direction
        if (targetIndex !in exercises.indices) return
        val exercise = exercises.removeAt(exerciseIndex)
        exercises.add(targetIndex, exercise)
        circuits[circuitIndex] = circuits[circuitIndex].copy(exercises = exercises)
        updateDraft(state.value.draft.copy(circuits = circuits))
    }

    suspend fun saveWorkout(): Long? {
        val validation = validator(state.value.draft)
        _state.update { it.copy(validation = validation, isSaving = true, saveError = null) }
        if (!validation.isValid) {
            _state.update { it.copy(isSaving = false, saveError = "Fix the validation errors before saving.") }
            return null
        }
        return runCatching {
            workoutRepository.saveWorkout(state.value.draft)
        }.onSuccess { workoutId ->
            updateDraft(state.value.draft.copy(id = workoutId))
            _state.update { it.copy(isSaving = false, saveError = null) }
        }.onFailure { error ->
            _state.update { it.copy(isSaving = false, saveError = error.message ?: "Unable to save workout.") }
        }.getOrNull()
    }

    private fun updateDraft(draft: WorkoutDraft) {
        _state.update {
            it.copy(
                draft = draft,
            )
        }
        validate()
    }

    private fun validate() {
        _state.update { it.copy(validation = validator(it.draft)) }
    }

    private fun defaultWorkoutDraft(): WorkoutDraft {
        return if (BuildConfig.DEBUG) {
            DefaultWorkoutDrafts.debugWorkoutDraft()
        } else {
            DefaultWorkoutDrafts.emptyWorkoutDraft()
        }
    }
}
