package dev.wwade.workout.data.repository

import dev.wwade.workout.data.db.CircuitSessionWithExercises
import dev.wwade.workout.data.db.CircuitTemplateWithExercises
import dev.wwade.workout.data.db.ExerciseDefinitionWithUsage
import dev.wwade.workout.data.db.ExerciseSetHistoryProjection
import dev.wwade.workout.data.db.ExerciseTemplateWithDefinition
import dev.wwade.workout.data.db.SetEntryEntity
import dev.wwade.workout.data.db.WorkoutSessionEntity
import dev.wwade.workout.data.db.WorkoutSessionWithChildren
import dev.wwade.workout.data.db.WorkoutTemplateEntity
import dev.wwade.workout.data.db.WorkoutTemplateWithChildren
import dev.wwade.workout.domain.model.CircuitSessionDetail
import dev.wwade.workout.domain.model.CircuitTemplate
import dev.wwade.workout.domain.model.CompletedSessionListItem
import dev.wwade.workout.domain.model.ExerciseDefinition
import dev.wwade.workout.domain.model.ExerciseSessionDetail
import dev.wwade.workout.domain.model.ExerciseSetHistoryItem
import dev.wwade.workout.domain.model.ExerciseTemplate
import dev.wwade.workout.domain.model.SetEntry
import dev.wwade.workout.domain.model.WorkoutListItem
import dev.wwade.workout.domain.model.WorkoutSessionDetail
import dev.wwade.workout.domain.model.WorkoutTemplate

fun WorkoutTemplateEntity.toListItem(
    circuitCount: Int,
    exerciseCount: Int,
): WorkoutListItem {
    return WorkoutListItem(
        id = id,
        name = name,
        circuitCount = circuitCount,
        exerciseCount = exerciseCount,
        updatedAt = updatedAt,
    )
}

fun WorkoutTemplateWithChildren.toDomain(): WorkoutTemplate {
    return WorkoutTemplate(
        id = workout.id,
        name = workout.name,
        sortOrder = workout.sortOrder,
        createdAt = workout.createdAt,
        updatedAt = workout.updatedAt,
        circuits = circuits.sortedBy { it.circuit.sortOrder }.map { it.toDomain(workout.id) },
    )
}

private fun CircuitTemplateWithExercises.toDomain(workoutId: Long): CircuitTemplate {
    return CircuitTemplate(
        id = circuit.id,
        workoutId = workoutId,
        name = circuit.name,
        sortOrder = circuit.sortOrder,
        exercises = exercises.sortedBy { it.exercise.sortOrder }.map { it.toDomain(circuit.id) },
    )
}

private fun ExerciseTemplateWithDefinition.toDomain(circuitId: Long): ExerciseTemplate {
    return ExerciseTemplate(
        id = exercise.id,
        circuitId = circuitId,
        exerciseDefinitionId = definition.id,
        name = definition.name,
        guidance = exercise.guidance.ifBlank { definition.defaultGuidance },
        guidanceOverride = exercise.guidance,
        repMin = exercise.repMin,
        repMax = exercise.repMax,
        loadKind = exercise.loadKind,
        loadMin = exercise.loadMin,
        loadMax = exercise.loadMax,
        loadUnit = exercise.loadUnit,
        restTimeSeconds = exercise.restTimeSeconds,
        setCount = exercise.setCount,
        sortOrder = exercise.sortOrder,
    )
}

fun WorkoutSessionWithChildren.toDomain(): WorkoutSessionDetail {
    return WorkoutSessionDetail(
        sessionId = session.id,
        workoutTemplateId = session.workoutTemplateId,
        workoutName = session.workoutNameSnapshot,
        startedAt = session.startedAt,
        completedAt = session.completedAt,
        status = session.status,
        circuits = circuits.sortedBy { it.circuit.sortOrder }.map { it.toDomain() },
    )
}

private fun CircuitSessionWithExercises.toDomain(): CircuitSessionDetail {
    return CircuitSessionDetail(
        circuitSessionId = circuit.id,
        circuitTemplateId = circuit.circuitTemplateId,
        name = circuit.circuitNameSnapshot,
        sortOrder = circuit.sortOrder,
        setCount = circuit.setCount,
        exercises = exercises.sortedBy { it.exercise.sortOrder }.map { exerciseWithSets ->
            ExerciseSessionDetail(
                exerciseSessionId = exerciseWithSets.exercise.id,
                exerciseTemplateId = exerciseWithSets.exercise.exerciseTemplateId,
                exerciseDefinitionId = exerciseWithSets.exercise.exerciseDefinitionId,
                name = exerciseWithSets.exercise.exerciseNameSnapshot,
                guidance = exerciseWithSets.exercise.guidanceSnapshot,
                repMin = exerciseWithSets.exercise.repMinSnapshot,
                repMax = exerciseWithSets.exercise.repMaxSnapshot,
                loadKind = exerciseWithSets.exercise.loadKindSnapshot,
                loadMin = exerciseWithSets.exercise.loadMinSnapshot,
                loadMax = exerciseWithSets.exercise.loadMaxSnapshot,
                loadUnit = exerciseWithSets.exercise.loadUnitSnapshot,
                restTimeSeconds = exerciseWithSets.exercise.restTimeSecondsSnapshot,
                sortOrder = exerciseWithSets.exercise.sortOrder,
                sets = exerciseWithSets.sets.sortedBy { it.setIndex }.map { it.toDomain() },
            )
        },
    )
}

fun SetEntryEntity.toDomain(): SetEntry {
    return SetEntry(
        id = id,
        exerciseSessionId = exerciseSessionId,
        setIndex = setIndex,
        repsActual = repsActual,
        loadActual = loadActual,
        notes = notes,
        skipped = skipped,
    )
}

fun ExerciseSetHistoryProjection.toDomain(): ExerciseSetHistoryItem {
    return ExerciseSetHistoryItem(
        workoutSessionId = workoutSessionId,
        workoutName = workoutName,
        completedAt = completedAt,
        setIndex = setIndex,
        repsActual = repsActual,
        loadActual = loadActual,
        notes = notes,
        skipped = skipped,
    )
}

fun ExerciseDefinitionWithUsage.toDomain(): ExerciseDefinition {
    return ExerciseDefinition(
        id = definition.id,
        name = definition.name,
        normalizedName = definition.normalizedName,
        defaultGuidance = definition.defaultGuidance,
        archived = definition.archived,
        createdAt = definition.createdAt,
        updatedAt = definition.updatedAt,
        usageCount = usageCount,
    )
}

fun WorkoutSessionEntity.toCompletedListItem(circuitCount: Int): CompletedSessionListItem {
    return CompletedSessionListItem(
        sessionId = id,
        workoutName = workoutNameSnapshot,
        startedAt = startedAt,
        completedAt = completedAt ?: startedAt,
        circuitCount = circuitCount,
    )
}
