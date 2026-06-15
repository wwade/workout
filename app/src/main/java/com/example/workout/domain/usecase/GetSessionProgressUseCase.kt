package com.example.workout.domain.usecase

import com.example.workout.domain.model.ExerciseProgressSnapshot
import com.example.workout.domain.model.SessionPositionSnapshot
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

    fun listPositions(detail: WorkoutSessionDetail): List<CurrentPosition> {
        return detail.circuits
            .sortedBy { it.sortOrder }
            .flatMapIndexed { circuitIndex, circuit ->
                List(circuit.setCount) { setIndex ->
                    CurrentPosition(circuitIndex = circuitIndex, setIndex = setIndex)
                }
            }
    }

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
        selectedPosition: CurrentPosition? = null,
        prefillProvider: suspend (Long?, Int) -> SetEntry?,
    ): SessionProgressSnapshot {
        val sortedCircuits = detail.circuits.sortedBy { it.sortOrder }
        val defaultPosition = findCurrentPosition(detail)
        val availablePositions = sortedCircuits.flatMapIndexed { circuitIndex, circuit ->
            List(circuit.setCount) { setIndex ->
                val isSaved = circuit.exercises.all { exercise ->
                    exercise.sets.any { it.setIndex == setIndex }
                }
                val isCurrentTarget =
                    defaultPosition?.circuitIndex == circuitIndex && defaultPosition.setIndex == setIndex
                SessionPositionSnapshot(
                    circuitIndex = circuitIndex,
                    setIndex = setIndex,
                    circuitName = circuit.name,
                    totalSetsInCircuit = circuit.setCount,
                    isSaved = isSaved,
                    isCurrentTarget = isCurrentTarget,
                    isSelectable = isSaved || isCurrentTarget || defaultPosition == null,
                )
            }
        }
        val fallbackPosition = defaultPosition ?: availablePositions.lastOrNull()?.let {
            CurrentPosition(circuitIndex = it.circuitIndex, setIndex = it.setIndex)
        }
        val position = selectedPosition ?: fallbackPosition
        val isCompleted = defaultPosition == null
        val activeCircuitIndex = position?.circuitIndex ?: 0
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
                availablePositions = emptyList(),
                exercises = emptyList(),
            )
        }
        val activeSetIndex = position?.setIndex ?: 0
        val exercises = activeCircuit.exercises.sortedBy { it.sortOrder }.map { exercise ->
            val currentSet = exercise.sets.firstOrNull { it.setIndex == activeSetIndex }
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
                suggestedReps = currentSet?.repsActual ?: previousSet?.repsActual ?: prefill?.repsActual,
                suggestedLoad = currentSet?.loadActual ?: previousSet?.loadActual ?: prefill?.loadActual,
                suggestedNotes = currentSet?.notes ?: prefill?.notes.orEmpty(),
                suggestedSkipped = currentSet?.skipped ?: false,
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
            availablePositions = availablePositions,
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
