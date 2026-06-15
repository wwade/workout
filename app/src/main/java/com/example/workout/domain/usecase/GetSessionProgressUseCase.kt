package com.example.workout.domain.usecase

import com.example.workout.domain.model.ExerciseProgressSnapshot
import com.example.workout.domain.model.SetEntry
import com.example.workout.domain.model.SessionProgressSnapshot
import com.example.workout.domain.model.WorkoutSessionDetail
import com.example.workout.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetSessionProgressUseCase(
    private val sessionRepository: SessionRepository,
) {
    operator fun invoke(sessionId: Long): Flow<SessionProgressSnapshot?> {
        return sessionRepository.observeSessionDetail(sessionId).map { detail ->
            detail?.let {
                SessionProgressCalculator.buildSnapshot(
                    detail = it,
                    prefillProvider = { exerciseTemplateId, setIndex ->
                        exerciseTemplateId?.let { id ->
                            sessionRepository.getLatestCompletedSetEntry(id, setIndex)
                        }
                    },
                )
            }
        }
    }
}

object SessionProgressCalculator {
    fun isCompleted(detail: WorkoutSessionDetail): Boolean = findCurrentPosition(detail) == null

    fun findCurrentPosition(detail: WorkoutSessionDetail): CurrentPosition? {
        val sortedCircuits = detail.circuits.sortedBy { it.sortOrder }
        sortedCircuits.forEachIndexed { circuitIndex, circuit ->
            val completedRounds = circuit.exercises
                .sortedBy { it.sortOrder }
                .minOfOrNull { exercise -> exercise.sets.map { it.setIndex }.distinct().count() } ?: 0
            if (completedRounds < circuit.setCount) {
                return CurrentPosition(
                    circuitIndex = circuitIndex,
                    setIndex = completedRounds,
                )
            }
        }
        return null
    }

    suspend fun buildSnapshot(
        detail: WorkoutSessionDetail,
        prefillProvider: suspend (Long?, Int) -> SetEntry?,
    ): SessionProgressSnapshot {
        val sortedCircuits = detail.circuits.sortedBy { it.sortOrder }
        val position = findCurrentPosition(detail)
        val isCompleted = position == null
        val activeCircuitIndex = position?.circuitIndex ?: (sortedCircuits.lastIndex).coerceAtLeast(0)
        val activeCircuit = sortedCircuits.getOrElse(activeCircuitIndex) {
            return SessionProgressSnapshot(
                sessionId = detail.sessionId,
                workoutName = detail.workoutName,
                currentCircuitIndex = 0,
                currentCircuitName = "",
                currentSetIndex = 0,
                totalSetsInCircuit = 0,
                isLastRound = true,
                isLastCircuit = true,
                isCompleted = true,
                exercises = emptyList(),
            )
        }
        val activeSetIndex = position?.setIndex ?: (activeCircuit.setCount - 1).coerceAtLeast(0)
        val exercises = activeCircuit.exercises.sortedBy { it.sortOrder }.map { exercise ->
            val prefill = prefillProvider(exercise.exerciseTemplateId, activeSetIndex)
            val previousSet = exercise.sets
                .filter { it.setIndex < activeSetIndex }
                .maxByOrNull { it.setIndex }
            ExerciseProgressSnapshot(
                exerciseSessionId = exercise.exerciseSessionId,
                exerciseTemplateId = exercise.exerciseTemplateId,
                exerciseName = exercise.name,
                guidance = exercise.guidance,
                repRangeLabel = "${exercise.repMin}-${exercise.repMax}",
                loadRangeLabel = buildLoadRangeLabel(exercise.loadMin, exercise.loadMax, exercise.loadUnit.name.lowercase()),
                loadUnit = exercise.loadUnit,
                restTimeSeconds = exercise.restTimeSeconds,
                suggestedReps = prefill?.repsActual,
                suggestedLoad = previousSet?.loadActual ?: prefill?.loadActual,
                suggestedNotes = prefill?.notes.orEmpty(),
                suggestedSkipped = prefill?.skipped ?: false,
            )
        }
        return SessionProgressSnapshot(
            sessionId = detail.sessionId,
            workoutName = detail.workoutName,
            currentCircuitIndex = activeCircuitIndex,
            currentCircuitName = activeCircuit.name,
            currentSetIndex = activeSetIndex,
            totalSetsInCircuit = activeCircuit.setCount,
            isLastRound = activeSetIndex == activeCircuit.setCount - 1,
            isLastCircuit = activeCircuitIndex == sortedCircuits.lastIndex,
            isCompleted = isCompleted,
            exercises = exercises,
        )
    }

    private fun buildLoadRangeLabel(loadMin: Double, loadMax: Double, unit: String): String {
        return "${formatLoad(loadMin)}-${formatLoad(loadMax)} $unit"
    }

    private fun formatLoad(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString()
        }
    }

    data class CurrentPosition(
        val circuitIndex: Int,
        val setIndex: Int,
    )
}
