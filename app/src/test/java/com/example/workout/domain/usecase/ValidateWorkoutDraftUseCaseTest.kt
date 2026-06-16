package dev.wwade.workout.domain.usecase

import dev.wwade.workout.domain.model.CircuitDraft
import dev.wwade.workout.domain.model.ExerciseDraft
import dev.wwade.workout.domain.model.LoadKind
import dev.wwade.workout.domain.model.LoadUnit
import dev.wwade.workout.domain.model.WorkoutDraft
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ValidateWorkoutDraftUseCaseTest {
    private val useCase = ValidateWorkoutDraftUseCase()

    @Test
    fun rejectsWorkoutWithoutCircuits() {
        val result = useCase(WorkoutDraft(name = "Leg Day"))

        assertThat(result.isValid).isFalse()
        assertThat(result.workoutError).isEqualTo("A workout needs at least one circuit.")
    }

    @Test
    fun rejectsCircuitWithMismatchedSetCounts() {
        val result = useCase(
            WorkoutDraft(
                name = "Part one",
                circuits = listOf(
                    CircuitDraft(
                        name = "Warmup",
                        exercises = listOf(
                            ExerciseDraft(name = "First", setCount = 3),
                            ExerciseDraft(name = "Second", setCount = 4),
                        ),
                    ),
                ),
            ),
        )

        assertThat(result.isValid).isFalse()
        assertThat(result.circuitValidations.single().setCountMismatch).isTrue()
    }

    @Test
    fun rejectsInvalidRepAndLoadRanges() {
        val result = useCase(
            WorkoutDraft(
                name = "Part one",
                circuits = listOf(
                    CircuitDraft(
                        name = "Warmup",
                        exercises = listOf(
                            ExerciseDraft(
                                name = "First",
                                repMin = 10,
                                repMax = 8,
                                loadMin = 50.0,
                                loadMax = 20.0,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val exerciseValidation = result.circuitValidations.single().exerciseValidations.single()
        assertThat(exerciseValidation.repRangeError).isNotNull()
        assertThat(exerciseValidation.loadRangeError).isNotNull()
    }

    @Test
    fun rejectsInvalidLoadUnitForDurationExercise() {
        val result = useCase(
            WorkoutDraft(
                name = "Part one",
                circuits = listOf(
                    CircuitDraft(
                        name = "Warmup",
                        exercises = listOf(
                            ExerciseDraft(
                                name = "Timer hold",
                                loadKind = LoadKind.DURATION,
                                loadUnit = LoadUnit.KG,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val validation = result.circuitValidations.single().exerciseValidations.single()
        assertThat(validation.loadUnitError).isEqualTo("Duration exercises must use seconds.")
    }

    @Test
    fun rejectsNegativeRestTime() {
        val result = useCase(
            WorkoutDraft(
                name = "Part one",
                circuits = listOf(
                    CircuitDraft(
                        name = "Warmup",
                        exercises = listOf(
                            ExerciseDraft(
                                name = "First",
                                restTimeSeconds = -15,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val validation = result.circuitValidations.single().exerciseValidations.single()
        assertThat(validation.restTimeError).isEqualTo("Rest time must be 0 seconds or more.")
    }
}
