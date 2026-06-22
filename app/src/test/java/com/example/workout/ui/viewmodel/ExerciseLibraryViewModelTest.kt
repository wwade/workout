package dev.wwade.workout.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.wwade.workout.domain.model.ExerciseDefinition
import dev.wwade.workout.domain.model.ExerciseDefinitionDraft
import dev.wwade.workout.domain.repository.ExerciseDefinitionRepository
import dev.wwade.workout.domain.repository.normalizeExerciseName
import dev.wwade.workout.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseLibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun filtersAndArchivesExercises() = runTest {
        val repository = FakeExerciseDefinitionRepository()
        val viewModel = ExerciseLibraryViewModel(repository)
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        viewModel.saveExercise(ExerciseDefinitionDraft(name = "Goblet Squat", defaultGuidance = "Stay tall"))
        viewModel.saveExercise(ExerciseDefinitionDraft(name = "Push-Up"))
        advanceUntilIdle()

        viewModel.updateSearchQuery("squat")
        advanceUntilIdle()
        assertThat(viewModel.state.value.filteredExercises.map { it.name }).containsExactly("Goblet Squat")

        val squatId = viewModel.state.value.filteredExercises.single().id
        viewModel.archiveExercise(squatId, archived = true)
        advanceUntilIdle()

        assertThat(viewModel.state.value.exercises.first { it.id == squatId }.archived).isTrue()
        collector.cancel()
    }
}

private class FakeExerciseDefinitionRepository : ExerciseDefinitionRepository {
    private val definitions = MutableStateFlow<List<ExerciseDefinition>>(emptyList())
    private var nextId = 1L

    override fun observeExerciseDefinitions(): Flow<List<ExerciseDefinition>> = definitions

    override suspend fun getExerciseDefinitions(): List<ExerciseDefinition> = definitions.value

    override suspend fun saveExerciseDefinition(definition: ExerciseDefinitionDraft): Long {
        val id = definition.id ?: nextId++
        val saved = ExerciseDefinition(
            id = id,
            name = definition.name.trim(),
            normalizedName = normalizeExerciseName(definition.name),
            defaultGuidance = definition.defaultGuidance.trim(),
            archived = definition.archived,
            createdAt = 1,
            updatedAt = 1,
        )
        definitions.value = definitions.value.filterNot { it.id == id } + saved
        return id
    }

    override suspend fun archiveExerciseDefinition(exerciseDefinitionId: Long, archived: Boolean) {
        definitions.value = definitions.value.map {
            if (it.id == exerciseDefinitionId) it.copy(archived = archived) else it
        }
    }
}
