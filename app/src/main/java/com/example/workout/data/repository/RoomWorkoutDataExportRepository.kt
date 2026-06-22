package dev.wwade.workout.data.repository

import dev.wwade.workout.data.db.ExerciseDefinitionDao
import dev.wwade.workout.data.db.WorkoutSessionDao
import dev.wwade.workout.data.db.WorkoutTemplateDao
import dev.wwade.workout.domain.exporter.WorkoutDataExportSnapshot
import dev.wwade.workout.domain.repository.WorkoutDataExportRepository

class RoomWorkoutDataExportRepository(
    private val exerciseDefinitionDao: ExerciseDefinitionDao,
    private val workoutTemplateDao: WorkoutTemplateDao,
    private val workoutSessionDao: WorkoutSessionDao,
) : WorkoutDataExportRepository {
    override suspend fun exportSnapshot(): WorkoutDataExportSnapshot {
        return WorkoutDataExportSnapshot(
            exerciseDefinitions = exerciseDefinitionDao.getExerciseDefinitionsWithUsage().map { it.toDomain() },
            workouts = workoutTemplateDao.getAllWorkoutTemplateGraphs().map { it.toDomain() },
            sessions = workoutSessionDao.getAllSessionDetails().map { it.toDomain() },
        )
    }
}
