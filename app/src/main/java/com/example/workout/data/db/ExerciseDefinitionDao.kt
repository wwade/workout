package dev.wwade.workout.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.wwade.workout.domain.model.ExerciseDefinitionDraft
import dev.wwade.workout.domain.repository.normalizeExerciseName
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ExerciseDefinitionDao {
    @Query(
        """
        SELECT ed.*, COUNT(et.id) AS usageCount
        FROM exercise_definitions ed
        LEFT JOIN exercise_templates et ON et.exerciseDefinitionId = ed.id
        GROUP BY ed.id
        ORDER BY ed.archived ASC, ed.name COLLATE NOCASE ASC
        """,
    )
    abstract fun observeExerciseDefinitionsWithUsage(): Flow<List<ExerciseDefinitionWithUsage>>

    @Query(
        """
        SELECT ed.*, COUNT(et.id) AS usageCount
        FROM exercise_definitions ed
        LEFT JOIN exercise_templates et ON et.exerciseDefinitionId = ed.id
        GROUP BY ed.id
        ORDER BY ed.archived ASC, ed.name COLLATE NOCASE ASC
        """,
    )
    abstract suspend fun getExerciseDefinitionsWithUsage(): List<ExerciseDefinitionWithUsage>

    @Query("SELECT * FROM exercise_definitions WHERE normalizedName = :normalizedName LIMIT 1")
    protected abstract suspend fun getByNormalizedName(normalizedName: String): ExerciseDefinitionEntity?

    @Query("SELECT * FROM exercise_definitions WHERE id = :id")
    protected abstract suspend fun getById(id: Long): ExerciseDefinitionEntity?

    @Insert
    protected abstract suspend fun insertDefinition(entity: ExerciseDefinitionEntity): Long

    @Update
    protected abstract suspend fun updateDefinition(entity: ExerciseDefinitionEntity)

    @Query("UPDATE exercise_definitions SET archived = :archived, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun updateArchived(id: Long, archived: Boolean, updatedAt: Long)

    @Transaction
    open suspend fun upsertDefinition(draft: ExerciseDefinitionDraft): Long {
        val now = System.currentTimeMillis()
        val name = draft.name.trim()
        val normalizedName = normalizeExerciseName(name)
        val existing = draft.id?.let { getById(it) }
            ?: getByNormalizedName(normalizedName)
        return if (existing == null) {
            insertDefinition(
                ExerciseDefinitionEntity(
                    name = name,
                    normalizedName = normalizedName,
                    defaultGuidance = draft.defaultGuidance.trim(),
                    archived = draft.archived,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            updateDefinition(
                existing.copy(
                    name = name,
                    normalizedName = normalizedName,
                    defaultGuidance = draft.defaultGuidance.trim(),
                    archived = draft.archived,
                    updatedAt = now,
                ),
            )
            existing.id
        }
    }

    @Transaction
    open suspend fun getOrCreateDefinition(
        name: String,
        defaultGuidance: String,
    ): ExerciseDefinitionEntity {
        val normalizedName = normalizeExerciseName(name)
        getByNormalizedName(normalizedName)?.let { return it }
        val now = System.currentTimeMillis()
        val id = insertDefinition(
            ExerciseDefinitionEntity(
                name = name.trim(),
                normalizedName = normalizedName,
                defaultGuidance = defaultGuidance.trim(),
                archived = false,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return getById(id) ?: error("Missing exercise definition $id")
    }
}
