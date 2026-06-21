package dev.wwade.workout.domain.exporter

import dev.wwade.workout.domain.model.CircuitSessionDetail
import dev.wwade.workout.domain.model.CircuitTemplate
import dev.wwade.workout.domain.model.ExerciseSessionDetail
import dev.wwade.workout.domain.model.ExerciseTemplate
import dev.wwade.workout.domain.model.SetEntry
import dev.wwade.workout.domain.model.WorkoutSessionDetail
import dev.wwade.workout.domain.model.WorkoutTemplate
import kotlinx.serialization.Serializable

data class WorkoutDataExportSnapshot(
    val workouts: List<WorkoutTemplate>,
    val sessions: List<WorkoutSessionDetail>,
)

data class WorkoutDataExportArtifact(
    val exportedAt: Long,
    val json: String,
)

@Serializable
data class WorkoutDataExportDto(
    val schemaVersion: Int,
    val exportedAt: Long,
    val workouts: List<WorkoutTemplateExportDto>,
    val sessions: List<WorkoutSessionExportDto>,
)

@Serializable
data class WorkoutTemplateExportDto(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val circuits: List<CircuitTemplateExportDto>,
)

@Serializable
data class CircuitTemplateExportDto(
    val id: Long,
    val workoutId: Long,
    val name: String,
    val sortOrder: Int,
    val exercises: List<ExerciseTemplateExportDto>,
)

@Serializable
data class ExerciseTemplateExportDto(
    val id: Long,
    val circuitId: Long,
    val name: String,
    val guidance: String,
    val repMin: Int,
    val repMax: Int,
    val loadKind: dev.wwade.workout.domain.model.LoadKind,
    val loadMin: Double,
    val loadMax: Double,
    val loadUnit: dev.wwade.workout.domain.model.LoadUnit,
    val restTimeSeconds: Int,
    val setCount: Int,
    val sortOrder: Int,
)

@Serializable
data class WorkoutSessionExportDto(
    val sessionId: Long,
    val workoutTemplateId: Long?,
    val workoutName: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: dev.wwade.workout.domain.model.SessionStatus,
    val circuits: List<CircuitSessionExportDto>,
)

@Serializable
data class CircuitSessionExportDto(
    val circuitSessionId: Long,
    val circuitTemplateId: Long?,
    val name: String,
    val sortOrder: Int,
    val setCount: Int,
    val exercises: List<ExerciseSessionExportDto>,
)

@Serializable
data class ExerciseSessionExportDto(
    val exerciseSessionId: Long,
    val exerciseTemplateId: Long?,
    val name: String,
    val guidance: String,
    val repMin: Int,
    val repMax: Int,
    val loadKind: dev.wwade.workout.domain.model.LoadKind,
    val loadMin: Double,
    val loadMax: Double,
    val loadUnit: dev.wwade.workout.domain.model.LoadUnit,
    val restTimeSeconds: Int,
    val sortOrder: Int,
    val sets: List<SetEntryExportDto>,
)

@Serializable
data class SetEntryExportDto(
    val id: Long,
    val exerciseSessionId: Long,
    val setIndex: Int,
    val repsActual: Int?,
    val loadActual: Double?,
    val notes: String,
    val skipped: Boolean,
)

fun WorkoutDataExportSnapshot.toDto(exportedAt: Long): WorkoutDataExportDto {
    return WorkoutDataExportDto(
        schemaVersion = 1,
        exportedAt = exportedAt,
        workouts = workouts.map { it.toDto() },
        sessions = sessions.map { it.toDto() },
    )
}

private fun WorkoutTemplate.toDto(): WorkoutTemplateExportDto {
    return WorkoutTemplateExportDto(
        id = id,
        name = name,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        circuits = circuits.map { it.toDto() },
    )
}

private fun CircuitTemplate.toDto(): CircuitTemplateExportDto {
    return CircuitTemplateExportDto(
        id = id,
        workoutId = workoutId,
        name = name,
        sortOrder = sortOrder,
        exercises = exercises.map { it.toDto() },
    )
}

private fun ExerciseTemplate.toDto(): ExerciseTemplateExportDto {
    return ExerciseTemplateExportDto(
        id = id,
        circuitId = circuitId,
        name = name,
        guidance = guidance,
        repMin = repMin,
        repMax = repMax,
        loadKind = loadKind,
        loadMin = loadMin,
        loadMax = loadMax,
        loadUnit = loadUnit,
        restTimeSeconds = restTimeSeconds,
        setCount = setCount,
        sortOrder = sortOrder,
    )
}

private fun WorkoutSessionDetail.toDto(): WorkoutSessionExportDto {
    return WorkoutSessionExportDto(
        sessionId = sessionId,
        workoutTemplateId = workoutTemplateId,
        workoutName = workoutName,
        startedAt = startedAt,
        completedAt = completedAt,
        status = status,
        circuits = circuits.map { it.toDto() },
    )
}

private fun CircuitSessionDetail.toDto(): CircuitSessionExportDto {
    return CircuitSessionExportDto(
        circuitSessionId = circuitSessionId,
        circuitTemplateId = circuitTemplateId,
        name = name,
        sortOrder = sortOrder,
        setCount = setCount,
        exercises = exercises.map { it.toDto() },
    )
}

private fun ExerciseSessionDetail.toDto(): ExerciseSessionExportDto {
    return ExerciseSessionExportDto(
        exerciseSessionId = exerciseSessionId,
        exerciseTemplateId = exerciseTemplateId,
        name = name,
        guidance = guidance,
        repMin = repMin,
        repMax = repMax,
        loadKind = loadKind,
        loadMin = loadMin,
        loadMax = loadMax,
        loadUnit = loadUnit,
        restTimeSeconds = restTimeSeconds,
        sortOrder = sortOrder,
        sets = sets.map { it.toDto() },
    )
}

private fun SetEntry.toDto(): SetEntryExportDto {
    return SetEntryExportDto(
        id = id,
        exerciseSessionId = exerciseSessionId,
        setIndex = setIndex,
        repsActual = repsActual,
        loadActual = loadActual,
        notes = notes,
        skipped = skipped,
    )
}
