package com.example.workout.domain.usecase

import com.example.workout.domain.model.CircuitDraft
import com.example.workout.domain.model.ExerciseDraft
import com.example.workout.domain.model.LoadKind
import com.example.workout.domain.model.LoadUnit
import com.example.workout.domain.model.WorkoutDraft

data class WorkoutValidationResult(
    val workoutNameError: String? = null,
    val workoutError: String? = null,
    val circuitValidations: List<CircuitValidation> = emptyList(),
) {
    val isValid: Boolean
        get() = workoutNameError == null &&
            workoutError == null &&
            circuitValidations.all { it.isValid }
}

data class CircuitValidation(
    val nameError: String? = null,
    val circuitError: String? = null,
    val setCountMismatch: Boolean = false,
    val exerciseValidations: List<ExerciseValidation> = emptyList(),
) {
    val isValid: Boolean
        get() = nameError == null &&
            circuitError == null &&
            !setCountMismatch &&
            exerciseValidations.all { it.isValid }
}

data class ExerciseValidation(
    val nameError: String? = null,
    val setCountError: String? = null,
    val repRangeError: String? = null,
    val loadRangeError: String? = null,
    val loadUnitError: String? = null,
    val restTimeError: String? = null,
) {
    val isValid: Boolean
        get() = nameError == null &&
            setCountError == null &&
            repRangeError == null &&
            loadRangeError == null &&
            loadUnitError == null &&
            restTimeError == null
}

class ValidateWorkoutDraftUseCase {
    operator fun invoke(workout: WorkoutDraft): WorkoutValidationResult {
        val workoutNameError = workout.name.trim().ifEmpty { "" }.let {
            if (it.isEmpty()) "Workout name is required." else null
        }
        val workoutError = if (workout.circuits.isEmpty()) {
            "A workout needs at least one circuit."
        } else {
            null
        }

        return WorkoutValidationResult(
            workoutNameError = workoutNameError,
            workoutError = workoutError,
            circuitValidations = workout.circuits.map(::validateCircuit),
        )
    }

    private fun validateCircuit(circuit: CircuitDraft): CircuitValidation {
        val exercises = circuit.exercises
        val nameError = if (circuit.name.trim().isEmpty()) "Circuit name is required." else null
        val circuitError = if (exercises.isEmpty()) "Each circuit needs at least one exercise." else null
        val setCountMismatch = exercises.map { it.setCount }.distinct().size > 1

        return CircuitValidation(
            nameError = nameError,
            circuitError = circuitError,
            setCountMismatch = setCountMismatch,
            exerciseValidations = exercises.map(::validateExercise),
        )
    }

    private fun validateExercise(exercise: ExerciseDraft): ExerciseValidation {
        return ExerciseValidation(
            nameError = if (exercise.name.trim().isEmpty()) "Exercise name is required." else null,
            setCountError = if (exercise.setCount < 1) "Set count must be at least 1." else null,
            repRangeError = if (exercise.repMin > exercise.repMax) "Rep minimum must be less than or equal to rep maximum." else null,
            loadRangeError = if (exercise.loadMin > exercise.loadMax) "Load minimum must be less than or equal to load maximum." else null,
            loadUnitError = validateLoadUnit(exercise),
            restTimeError = if (exercise.restTimeSeconds < 0) "Rest time must be 0 seconds or more." else null,
        )
    }

    private fun validateLoadUnit(exercise: ExerciseDraft): String? {
        return when (exercise.loadKind) {
            LoadKind.WEIGHT -> if (exercise.loadUnit == LoadUnit.SEC) {
                "Weight exercises must use LB or KG."
            } else {
                null
            }

            LoadKind.DURATION -> if (exercise.loadUnit != LoadUnit.SEC) {
                "Duration exercises must use seconds."
            } else {
                null
            }
        }
    }
}
