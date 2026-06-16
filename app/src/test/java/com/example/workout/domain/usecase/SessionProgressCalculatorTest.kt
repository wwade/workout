package dev.wwade.workout.domain.usecase

import dev.wwade.workout.domain.model.CircuitSessionDetail
import dev.wwade.workout.domain.model.ExerciseSessionDetail
import dev.wwade.workout.domain.model.LoadKind
import dev.wwade.workout.domain.model.LoadUnit
import dev.wwade.workout.domain.model.SessionStatus
import dev.wwade.workout.domain.model.SetEntry
import dev.wwade.workout.domain.model.WorkoutSessionDetail
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SessionProgressCalculatorTest {
    @Test
    fun advancesInterleavedRoundsAcrossExercises() = runTest {
        val detail = sessionDetail(
            setCount = 3,
            firstExerciseSets = listOf(setEntry(0, 0)),
            secondExerciseSets = listOf(setEntry(1, 0)),
        )

        val position = SessionProgressCalculator.findCurrentPosition(detail)

        assertThat(position?.circuitIndex).isEqualTo(0)
        assertThat(position?.setIndex).isEqualTo(1)
    }

    @Test
    fun advancesToNextCircuitAfterFinalRound() = runTest {
        val detail = WorkoutSessionDetail(
            sessionId = 1,
            workoutTemplateId = 10,
            workoutName = "Part one",
            startedAt = 0,
            completedAt = null,
            status = SessionStatus.IN_PROGRESS,
            circuits = listOf(
                circuit(
                    id = 1,
                    name = "Warmup",
                    setCount = 1,
                    setEntries = listOf(
                        listOf(setEntry(0, 0)),
                        listOf(setEntry(1, 0)),
                    ),
                ),
                circuit(
                    id = 2,
                    name = "Strength",
                    setCount = 2,
                    setEntries = listOf(emptyList(), emptyList()),
                ),
            ),
        )

        val position = SessionProgressCalculator.findCurrentPosition(detail)

        assertThat(position?.circuitIndex).isEqualTo(1)
        assertThat(position?.setIndex).isEqualTo(0)
    }

    @Test
    fun marksSessionCompletedAfterLastCircuit() = runTest {
        val detail = WorkoutSessionDetail(
            sessionId = 1,
            workoutTemplateId = 10,
            workoutName = "Part one",
            startedAt = 0,
            completedAt = null,
            status = SessionStatus.IN_PROGRESS,
            circuits = listOf(
                circuit(
                    id = 1,
                    name = "Warmup",
                    setCount = 1,
                    setEntries = listOf(
                        listOf(setEntry(0, 0)),
                        listOf(setEntry(1, 0)),
                    ),
                ),
            ),
        )

        assertThat(SessionProgressCalculator.isCompleted(detail)).isTrue()
    }

    @Test
    fun prefersPreviousSetValuesForNextRound() = runTest {
        val detail = sessionDetail(
            setCount = 3,
            firstExerciseSets = listOf(setEntry(0, 0, repsActual = 7, loadActual = 42.5)),
            secondExerciseSets = listOf(setEntry(1, 0, repsActual = 9, loadActual = 55.0)),
        )

        val snapshot = SessionProgressCalculator.buildSnapshot(
            detail = detail,
            prefillProvider = { _, _ -> null },
        )

        assertThat(snapshot.currentSetIndex).isEqualTo(1)
        assertThat(snapshot.exercises[0].suggestedReps).isEqualTo(7)
        assertThat(snapshot.exercises[0].suggestedLoad).isEqualTo(42.5)
        assertThat(snapshot.exercises[1].suggestedReps).isEqualTo(9)
        assertThat(snapshot.exercises[1].suggestedLoad).isEqualTo(55.0)
    }

    @Test
    fun doesNotCarrySkippedStateFromHistory() = runTest {
        val detail = sessionDetail(
            setCount = 3,
            firstExerciseSets = emptyList(),
            secondExerciseSets = emptyList(),
        )

        val snapshot = SessionProgressCalculator.buildSnapshot(
            detail = detail,
            prefillProvider = { _, _ ->
                setEntry(
                    exerciseSessionId = 999,
                    setIndex = 0,
                    loadActual = 30.0,
                    skipped = true,
                )
            },
        )

        assertThat(snapshot.exercises.all { !it.suggestedSkipped }).isTrue()
    }

    private fun sessionDetail(
        setCount: Int,
        firstExerciseSets: List<SetEntry>,
        secondExerciseSets: List<SetEntry>,
    ): WorkoutSessionDetail {
        return WorkoutSessionDetail(
            sessionId = 1,
            workoutTemplateId = 10,
            workoutName = "Part one",
            startedAt = 0,
            completedAt = null,
            status = SessionStatus.IN_PROGRESS,
            circuits = listOf(
                circuit(
                    id = 1,
                    name = "Warmup",
                    setCount = setCount,
                    setEntries = listOf(firstExerciseSets, secondExerciseSets),
                ),
            ),
        )
    }

    private fun circuit(
        id: Long,
        name: String,
        setCount: Int,
        setEntries: List<List<SetEntry>>,
    ): CircuitSessionDetail {
        return CircuitSessionDetail(
            circuitSessionId = id,
            circuitTemplateId = id,
            name = name,
            sortOrder = id.toInt(),
            setCount = setCount,
            exercises = setEntries.mapIndexed { index, entries ->
                ExerciseSessionDetail(
                    exerciseSessionId = index.toLong() + 1,
                    exerciseTemplateId = index.toLong() + 100,
                    name = "Exercise ${index + 1}",
                    guidance = "",
                    repMin = 6,
                    repMax = 8,
                    loadKind = LoadKind.WEIGHT,
                    loadMin = 20.0,
                    loadMax = 30.0,
                    loadUnit = LoadUnit.KG,
                    restTimeSeconds = 60,
                    sortOrder = index,
                    sets = entries,
                )
            },
        )
    }

    private fun setEntry(
        exerciseSessionId: Long,
        setIndex: Int,
        repsActual: Int = 8,
        loadActual: Double = 30.0,
        skipped: Boolean = false,
    ): SetEntry {
        return SetEntry(
            id = setIndex.toLong() + 1,
            exerciseSessionId = exerciseSessionId,
            setIndex = setIndex,
            repsActual = repsActual,
            loadActual = loadActual,
            notes = "",
            skipped = skipped,
        )
    }
}
