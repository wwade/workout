package dev.wwade.workout.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wwade.workout.domain.model.ExerciseDefinitionDraft
import dev.wwade.workout.domain.repository.ExerciseDefinitionRepository
import dev.wwade.workout.ui.state.ExerciseLibraryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class ExerciseLibraryViewModel(
    private val exerciseDefinitionRepository: ExerciseDefinitionRepository,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val saveError = MutableStateFlow<String?>(null)

    val state: StateFlow<ExerciseLibraryState> = combine(
        exerciseDefinitionRepository.observeExerciseDefinitions(),
        searchQuery,
        saveError,
    ) { exercises, query, error ->
        ExerciseLibraryState(
            exercises = exercises,
            searchQuery = query,
            saveError = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExerciseLibraryState(),
    )

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    suspend fun saveExercise(draft: ExerciseDefinitionDraft): Boolean {
        if (draft.name.trim().isEmpty()) {
            saveError.value = "Exercise name is required."
            return false
        }
        return runCatching {
            exerciseDefinitionRepository.saveExerciseDefinition(draft)
        }.onSuccess {
            saveError.value = null
        }.onFailure { error ->
            saveError.value = error.message ?: "Unable to save exercise."
        }.isSuccess
    }

    suspend fun archiveExercise(exerciseDefinitionId: Long, archived: Boolean) {
        runCatching {
            exerciseDefinitionRepository.archiveExerciseDefinition(exerciseDefinitionId, archived)
        }.onFailure { error ->
            saveError.value = error.message ?: "Unable to update exercise."
        }
    }

    fun clearError() {
        saveError.update { null }
    }
}
