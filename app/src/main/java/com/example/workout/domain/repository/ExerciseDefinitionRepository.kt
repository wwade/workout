package dev.wwade.workout.domain.repository

import dev.wwade.workout.domain.model.ExerciseDefinition
import dev.wwade.workout.domain.model.ExerciseDefinitionDraft
import kotlinx.coroutines.flow.Flow

interface ExerciseDefinitionRepository {
    fun observeExerciseDefinitions(): Flow<List<ExerciseDefinition>>
    suspend fun getExerciseDefinitions(): List<ExerciseDefinition>
    suspend fun saveExerciseDefinition(definition: ExerciseDefinitionDraft): Long
    suspend fun archiveExerciseDefinition(exerciseDefinitionId: Long, archived: Boolean)
}

fun normalizeExerciseName(name: String): String {
    return name.trim()
        .replace(Regex("\\s+"), " ")
        .lowercase()
}
