package dev.wwade.workout.domain.repository

import dev.wwade.workout.domain.importer.WorkoutDataRestoreCounts
import dev.wwade.workout.domain.importer.WorkoutDataRestoreSnapshot

interface WorkoutDataImportRepository {
    suspend fun restoreSnapshot(snapshot: WorkoutDataRestoreSnapshot): WorkoutDataRestoreCounts
}
