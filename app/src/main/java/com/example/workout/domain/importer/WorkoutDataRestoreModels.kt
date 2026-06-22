package dev.wwade.workout.domain.importer

import dev.wwade.workout.domain.model.LoadKind
import dev.wwade.workout.domain.model.LoadUnit
import dev.wwade.workout.domain.model.SessionStatus

sealed interface ParsedWorkoutImport {
    data class TemplateWorkouts(val workouts: List<dev.wwade.workout.domain.model.WorkoutDraft>) :
        ParsedWorkoutImport

    data class FullBackup(val snapshot: WorkoutDataRestoreSnapshot) : ParsedWorkoutImport
}

data class WorkoutDataRestoreSnapshot(
    val exerciseDefinitions: List<ExerciseDefinitionRestoreRow>,
    val workouts: List<WorkoutTemplateRestoreRow>,
    val sessions: List<WorkoutSessionRestoreRow>,
)

data class ExerciseDefinitionRestoreRow(
    val id: Long,
    val name: String,
    val defaultGuidance: String,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class WorkoutTemplateRestoreRow(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val circuits: List<CircuitTemplateRestoreRow>,
)

data class CircuitTemplateRestoreRow(
    val id: Long,
    val workoutId: Long,
    val name: String,
    val sortOrder: Int,
    val exercises: List<ExerciseTemplateRestoreRow>,
)

data class ExerciseTemplateRestoreRow(
    val id: Long,
    val circuitId: Long,
    val exerciseDefinitionId: Long,
    val guidance: String,
    val repMin: Int,
    val repMax: Int,
    val loadKind: LoadKind,
    val loadMin: Double,
    val loadMax: Double,
    val loadUnit: LoadUnit,
    val restTimeSeconds: Int,
    val setCount: Int,
    val sortOrder: Int,
)

data class WorkoutSessionRestoreRow(
    val sessionId: Long,
    val workoutTemplateId: Long?,
    val workoutName: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: SessionStatus,
    val circuits: List<CircuitSessionRestoreRow>,
)

data class CircuitSessionRestoreRow(
    val circuitSessionId: Long,
    val circuitTemplateId: Long?,
    val name: String,
    val sortOrder: Int,
    val setCount: Int,
    val exercises: List<ExerciseSessionRestoreRow>,
)

data class ExerciseSessionRestoreRow(
    val exerciseSessionId: Long,
    val exerciseTemplateId: Long?,
    val exerciseDefinitionId: Long?,
    val name: String,
    val guidance: String,
    val repMin: Int,
    val repMax: Int,
    val loadKind: LoadKind,
    val loadMin: Double,
    val loadMax: Double,
    val loadUnit: LoadUnit,
    val restTimeSeconds: Int,
    val sortOrder: Int,
    val sets: List<SetEntryRestoreRow>,
)

data class SetEntryRestoreRow(
    val id: Long,
    val exerciseSessionId: Long,
    val setIndex: Int,
    val repsActual: Int?,
    val loadActual: Double?,
    val notes: String,
    val skipped: Boolean,
)
