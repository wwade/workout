package dev.wwade.workout.data.repository

import dev.wwade.workout.data.db.ExerciseDefinitionDao
import dev.wwade.workout.domain.model.ExerciseDefinition
import dev.wwade.workout.domain.model.ExerciseDefinitionDraft
import dev.wwade.workout.domain.repository.ExerciseDefinitionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomExerciseDefinitionRepository(
    private val exerciseDefinitionDao: ExerciseDefinitionDao,
) : ExerciseDefinitionRepository {
    override fun observeExerciseDefinitions(): Flow<List<ExerciseDefinition>> {
        return exerciseDefinitionDao.observeExerciseDefinitionsWithUsage().map { definitions ->
            definitions.map { it.toDomain() }
        }
    }

    override suspend fun getExerciseDefinitions(): List<ExerciseDefinition> {
        return exerciseDefinitionDao.getExerciseDefinitionsWithUsage().map { it.toDomain() }
    }

    override suspend fun saveExerciseDefinition(definition: ExerciseDefinitionDraft): Long {
        return exerciseDefinitionDao.upsertDefinition(definition)
    }

    override suspend fun archiveExerciseDefinition(exerciseDefinitionId: Long, archived: Boolean) {
        exerciseDefinitionDao.updateArchived(
            id = exerciseDefinitionId,
            archived = archived,
            updatedAt = System.currentTimeMillis(),
        )
    }
}
