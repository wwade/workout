package dev.wwade.workout.data.repository

import dev.wwade.workout.data.db.WorkoutSessionDao
import dev.wwade.workout.data.db.WorkoutTemplateDao
import dev.wwade.workout.domain.exporter.WorkoutDataExportSnapshot
import dev.wwade.workout.domain.repository.WorkoutDataExportRepository

class RoomWorkoutDataExportRepository(
    private val workoutTemplateDao: WorkoutTemplateDao,
    private val workoutSessionDao: WorkoutSessionDao,
) : WorkoutDataExportRepository {
    override suspend fun exportSnapshot(): WorkoutDataExportSnapshot {
        return WorkoutDataExportSnapshot(
            workouts = workoutTemplateDao.getAllWorkoutTemplateGraphs().map { it.toDomain() },
            sessions = workoutSessionDao.getAllSessionDetails().map { it.toDomain() },
        )
    }
}
