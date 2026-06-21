package dev.wwade.workout.domain.repository

import dev.wwade.workout.domain.exporter.WorkoutDataExportSnapshot

interface WorkoutDataExportRepository {
    suspend fun exportSnapshot(): WorkoutDataExportSnapshot
}
