package dev.wwade.workout.data.repository

import androidx.room.withTransaction
import dev.wwade.workout.data.db.AppDatabase
import dev.wwade.workout.data.db.CircuitSessionEntity
import dev.wwade.workout.data.db.CircuitTemplateEntity
import dev.wwade.workout.data.db.ExerciseDefinitionEntity
import dev.wwade.workout.data.db.ExerciseSessionEntity
import dev.wwade.workout.data.db.ExerciseTemplateEntity
import dev.wwade.workout.data.db.SetEntryEntity
import dev.wwade.workout.data.db.WorkoutSessionEntity
import dev.wwade.workout.data.db.WorkoutTemplateEntity
import dev.wwade.workout.domain.importer.CircuitSessionRestoreRow
import dev.wwade.workout.domain.importer.CircuitTemplateRestoreRow
import dev.wwade.workout.domain.importer.ExerciseDefinitionRestoreRow
import dev.wwade.workout.domain.importer.ExerciseSessionRestoreRow
import dev.wwade.workout.domain.importer.ExerciseTemplateRestoreRow
import dev.wwade.workout.domain.importer.SetEntryRestoreRow
import dev.wwade.workout.domain.importer.WorkoutDataRestoreCounts
import dev.wwade.workout.domain.importer.WorkoutDataRestoreSnapshot
import dev.wwade.workout.domain.importer.WorkoutSessionRestoreRow
import dev.wwade.workout.domain.importer.WorkoutTemplateRestoreRow
import dev.wwade.workout.domain.repository.WorkoutDataImportRepository
import dev.wwade.workout.domain.repository.normalizeExerciseName

class RoomWorkoutDataImportRepository(
    private val database: AppDatabase,
) : WorkoutDataImportRepository {
    private val importDao = database.workoutDataImportDao()

    override suspend fun restoreSnapshot(snapshot: WorkoutDataRestoreSnapshot): WorkoutDataRestoreCounts {
        database.withTransaction {
            importDao.deleteSetEntries()
            importDao.deleteExerciseSessions()
            importDao.deleteCircuitSessions()
            importDao.deleteWorkoutSessions()
            importDao.deleteExerciseTemplates()
            importDao.deleteCircuitTemplates()
            importDao.deleteWorkoutTemplates()
            importDao.deleteExerciseDefinitions()

            importDao.insertExerciseDefinitions(snapshot.exerciseDefinitions.map { it.toEntity() })
            importDao.insertWorkoutTemplates(snapshot.workouts.map { it.toEntity() })
            importDao.insertCircuitTemplates(snapshot.workouts.flatMap { workout ->
                workout.circuits.map { it.toEntity() }
            })
            importDao.insertExerciseTemplates(snapshot.workouts.flatMap { workout ->
                workout.circuits.flatMap { circuit -> circuit.exercises.map { it.toEntity() } }
            })
            importDao.insertWorkoutSessions(snapshot.sessions.map { it.toEntity() })
            importDao.insertCircuitSessions(snapshot.sessions.flatMap { session ->
                session.circuits.map { circuit -> circuit.toEntity(session.sessionId) }
            })
            importDao.insertExerciseSessions(snapshot.sessions.flatMap { session ->
                session.circuits.flatMap { circuit ->
                    circuit.exercises.map { exercise -> exercise.toEntity(circuit.circuitSessionId) }
                }
            })
            importDao.insertSetEntries(snapshot.sessions.flatMap { session ->
                session.circuits.flatMap { circuit ->
                    circuit.exercises.flatMap { exercise -> exercise.sets.map { it.toEntity() } }
                }
            })
        }

        return WorkoutDataRestoreCounts(
            exerciseDefinitionCount = snapshot.exerciseDefinitions.size,
            workoutCount = snapshot.workouts.size,
            sessionCount = snapshot.sessions.size,
        )
    }
}

private fun ExerciseDefinitionRestoreRow.toEntity(): ExerciseDefinitionEntity {
    return ExerciseDefinitionEntity(
        id = id,
        name = name,
        normalizedName = normalizeExerciseName(name),
        defaultGuidance = defaultGuidance,
        archived = archived,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun WorkoutTemplateRestoreRow.toEntity(): WorkoutTemplateEntity {
    return WorkoutTemplateEntity(
        id = id,
        name = name,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun CircuitTemplateRestoreRow.toEntity(): CircuitTemplateEntity {
    return CircuitTemplateEntity(
        id = id,
        workoutId = workoutId,
        name = name,
        sortOrder = sortOrder,
    )
}

private fun ExerciseTemplateRestoreRow.toEntity(): ExerciseTemplateEntity {
    return ExerciseTemplateEntity(
        id = id,
        circuitId = circuitId,
        exerciseDefinitionId = exerciseDefinitionId,
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

private fun WorkoutSessionRestoreRow.toEntity(): WorkoutSessionEntity {
    return WorkoutSessionEntity(
        id = sessionId,
        workoutTemplateId = workoutTemplateId,
        workoutNameSnapshot = workoutName,
        startedAt = startedAt,
        completedAt = completedAt,
        status = status,
    )
}

private fun CircuitSessionRestoreRow.toEntity(workoutSessionId: Long): CircuitSessionEntity {
    return CircuitSessionEntity(
        id = circuitSessionId,
        workoutSessionId = workoutSessionId,
        circuitTemplateId = circuitTemplateId,
        circuitNameSnapshot = name,
        sortOrder = sortOrder,
        setCount = setCount,
    )
}

private fun ExerciseSessionRestoreRow.toEntity(circuitSessionId: Long): ExerciseSessionEntity {
    return ExerciseSessionEntity(
        id = exerciseSessionId,
        circuitSessionId = circuitSessionId,
        exerciseTemplateId = exerciseTemplateId,
        exerciseDefinitionId = exerciseDefinitionId,
        exerciseNameSnapshot = name,
        guidanceSnapshot = guidance,
        repMinSnapshot = repMin,
        repMaxSnapshot = repMax,
        loadKindSnapshot = loadKind,
        loadMinSnapshot = loadMin,
        loadMaxSnapshot = loadMax,
        loadUnitSnapshot = loadUnit,
        restTimeSecondsSnapshot = restTimeSeconds,
        sortOrder = sortOrder,
    )
}

private fun SetEntryRestoreRow.toEntity(): SetEntryEntity {
    return SetEntryEntity(
        id = id,
        exerciseSessionId = exerciseSessionId,
        setIndex = setIndex,
        repsActual = repsActual,
        loadActual = loadActual,
        notes = notes,
        skipped = skipped,
    )
}
