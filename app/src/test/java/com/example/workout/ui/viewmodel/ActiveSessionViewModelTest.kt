package com.example.workout.ui.viewmodel

import com.example.workout.domain.model.CircuitSessionDetail
import com.example.workout.domain.model.CompletedSessionListItem
import com.example.workout.domain.model.ExerciseSessionDetail
import com.example.workout.domain.model.LoadKind
import com.example.workout.domain.model.LoadUnit
import com.example.workout.domain.model.SessionStatus
import com.example.workout.domain.model.SetEntry
import com.example.workout.domain.model.SetEntryDraft
import com.example.workout.domain.model.WorkoutSessionDetail
import com.example.workout.domain.repository.SessionRepository
import com.example.workout.util.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun updateRepsKeepsSuggestedLoadIntact() = runTest {
        val repository = FakeSessionRepository(sessionDetail())
        val viewModel = ActiveSessionViewModel(
            sessionRepository = repository,
            sessionId = 1,
        )
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        advanceUntilIdle()
        viewModel.updateReps(exerciseSessionId = 1, value = "9")
        advanceUntilIdle()

        val exercise = viewModel.state.value.exerciseCards.single()
        assertThat(exercise.repsInput).isEqualTo("9")
        assertThat(exercise.loadInput).isEqualTo("30")
        collector.cancel()
    }

    @Test
    fun selectingCompletedRoundWithoutEditsDisablesSave() = runTest {
        val repository = FakeSessionRepository(sessionDetailWithSavedRounds())
        val viewModel = ActiveSessionViewModel(
            sessionRepository = repository,
            sessionId = 1,
        )
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        advanceUntilIdle()
        viewModel.selectRound(circuitIndex = 0, setIndex = 0)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.currentCircuitName).isEqualTo("Cycle 1")
        assertThat(state.currentSetIndex).isEqualTo(0)
        assertThat(state.exerciseCards.single().repsInput).isEqualTo("7")
        assertThat(state.canSaveRound).isFalse()
        collector.cancel()
    }

    @Test
    fun nextAndPreviousRoundNavigateAcrossCircuits() = runTest {
        val repository = FakeSessionRepository(sessionDetailWithTwoCircuits())
        val viewModel = ActiveSessionViewModel(
            sessionRepository = repository,
            sessionId = 1,
        )
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        advanceUntilIdle()
        assertThat(viewModel.state.value.currentCircuitName).isEqualTo("Cycle 2")
        assertThat(viewModel.state.value.currentSetIndex).isEqualTo(0)

        viewModel.goToPreviousRound()
        advanceUntilIdle()
        assertThat(viewModel.state.value.currentCircuitName).isEqualTo("Cycle 1")
        assertThat(viewModel.state.value.currentSetIndex).isEqualTo(1)

        viewModel.goToNextRound()
        advanceUntilIdle()
        assertThat(viewModel.state.value.currentCircuitName).isEqualTo("Cycle 2")
        assertThat(viewModel.state.value.currentSetIndex).isEqualTo(0)
        assertThat(viewModel.state.value.canGoForward).isFalse()
        collector.cancel()
    }

    @Test
    fun futureUnsavedRoundsAreMarkedUpcomingAndNotSelectable() = runTest {
        val repository = FakeSessionRepository(sessionDetailWithTwoCircuits())
        val viewModel = ActiveSessionViewModel(
            sessionRepository = repository,
            sessionId = 1,
        )
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        advanceUntilIdle()

        val options = viewModel.state.value.positionOptions
        assertThat(options.map { it.label to it.statusLabel }).containsExactly(
            "Cycle 1 - Set 1" to "Saved",
            "Cycle 1 - Set 2" to "Saved",
            "Cycle 2 - Set 1" to "Current",
            "Cycle 2 - Set 2" to "Upcoming",
        ).inOrder()
        assertThat(options.last().isSelectable).isFalse()

        viewModel.selectRound(circuitIndex = 1, setIndex = 1)
        advanceUntilIdle()
        assertThat(viewModel.state.value.currentCircuitName).isEqualTo("Cycle 2")
        assertThat(viewModel.state.value.currentSetIndex).isEqualTo(0)
        collector.cancel()
    }

    private fun sessionDetail(): WorkoutSessionDetail {
        return WorkoutSessionDetail(
            sessionId = 1,
            workoutTemplateId = 10,
            workoutName = "Part one",
            startedAt = 0L,
            completedAt = null,
            status = SessionStatus.IN_PROGRESS,
            circuits = listOf(
                CircuitSessionDetail(
                    circuitSessionId = 1,
                    circuitTemplateId = 1,
                    name = "Cycle 1",
                    sortOrder = 0,
                    setCount = 3,
                    exercises = listOf(
                        ExerciseSessionDetail(
                            exerciseSessionId = 1,
                            exerciseTemplateId = 101,
                            name = "Press",
                            guidance = "",
                            repMin = 6,
                            repMax = 8,
                            loadKind = LoadKind.WEIGHT,
                            loadMin = 20.0,
                            loadMax = 40.0,
                            loadUnit = LoadUnit.LB,
                            restTimeSeconds = 60,
                            sortOrder = 0,
                            sets = emptyList(),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun sessionDetailWithSavedRounds(): WorkoutSessionDetail {
        return WorkoutSessionDetail(
            sessionId = 1,
            workoutTemplateId = 10,
            workoutName = "Part one",
            startedAt = 0L,
            completedAt = null,
            status = SessionStatus.IN_PROGRESS,
            circuits = listOf(
                CircuitSessionDetail(
                    circuitSessionId = 1,
                    circuitTemplateId = 1,
                    name = "Cycle 1",
                    sortOrder = 0,
                    setCount = 3,
                    exercises = listOf(
                        ExerciseSessionDetail(
                            exerciseSessionId = 1,
                            exerciseTemplateId = 101,
                            name = "Press",
                            guidance = "",
                            repMin = 6,
                            repMax = 8,
                            loadKind = LoadKind.WEIGHT,
                            loadMin = 20.0,
                            loadMax = 40.0,
                            loadUnit = LoadUnit.LB,
                            restTimeSeconds = 60,
                            sortOrder = 0,
                            sets = listOf(
                                SetEntry(
                                    id = 1,
                                    exerciseSessionId = 1,
                                    setIndex = 0,
                                    repsActual = 7,
                                    loadActual = 25.0,
                                    notes = "steady",
                                    skipped = false,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun sessionDetailWithTwoCircuits(): WorkoutSessionDetail {
        return WorkoutSessionDetail(
            sessionId = 1,
            workoutTemplateId = 10,
            workoutName = "Part one",
            startedAt = 0L,
            completedAt = null,
            status = SessionStatus.IN_PROGRESS,
            circuits = listOf(
                CircuitSessionDetail(
                    circuitSessionId = 1,
                    circuitTemplateId = 1,
                    name = "Cycle 1",
                    sortOrder = 0,
                    setCount = 2,
                    exercises = listOf(
                        ExerciseSessionDetail(
                            exerciseSessionId = 1,
                            exerciseTemplateId = 101,
                            name = "Press",
                            guidance = "",
                            repMin = 6,
                            repMax = 8,
                            loadKind = LoadKind.WEIGHT,
                            loadMin = 20.0,
                            loadMax = 40.0,
                            loadUnit = LoadUnit.LB,
                            restTimeSeconds = 60,
                            sortOrder = 0,
                            sets = listOf(
                                SetEntry(
                                    id = 1,
                                    exerciseSessionId = 1,
                                    setIndex = 0,
                                    repsActual = 7,
                                    loadActual = 25.0,
                                    notes = "",
                                    skipped = false,
                                ),
                                SetEntry(
                                    id = 2,
                                    exerciseSessionId = 1,
                                    setIndex = 1,
                                    repsActual = 8,
                                    loadActual = 30.0,
                                    notes = "",
                                    skipped = false,
                                ),
                            ),
                        ),
                    ),
                ),
                CircuitSessionDetail(
                    circuitSessionId = 2,
                    circuitTemplateId = 2,
                    name = "Cycle 2",
                    sortOrder = 1,
                    setCount = 2,
                    exercises = listOf(
                        ExerciseSessionDetail(
                            exerciseSessionId = 2,
                            exerciseTemplateId = 102,
                            name = "Row",
                            guidance = "",
                            repMin = 8,
                            repMax = 10,
                            loadKind = LoadKind.WEIGHT,
                            loadMin = 30.0,
                            loadMax = 50.0,
                            loadUnit = LoadUnit.LB,
                            restTimeSeconds = 60,
                            sortOrder = 0,
                            sets = emptyList(),
                        ),
                    ),
                ),
            ),
        )
    }
}

private class FakeSessionRepository(
    detail: WorkoutSessionDetail,
) : SessionRepository {
    private val detailFlow = MutableStateFlow(detail)

    override fun observeActiveSessionId(): Flow<Long?> = flowOf(detailFlow.value.sessionId)

    override suspend fun startWorkout(workoutId: Long): Long = error("Not needed in test")

    override suspend fun abandonSession(sessionId: Long) = Unit

    override suspend fun saveRoundEntries(sessionId: Long, entries: List<SetEntryDraft>) {
        detailFlow.value = detailFlow.value.copy(
            circuits = detailFlow.value.circuits.map { circuit ->
                circuit.copy(
                    exercises = circuit.exercises.map { exercise ->
                        val matchingEntries = entries.filter { it.exerciseSessionId == exercise.exerciseSessionId }
                        if (matchingEntries.isEmpty()) {
                            exercise
                        } else {
                            exercise.copy(
                                sets = (exercise.sets.filter { existing ->
                                    matchingEntries.none { it.setIndex == existing.setIndex }
                                } + matchingEntries.mapIndexed { index, entry ->
                                    SetEntry(
                                        id = 10_000L + index,
                                        exerciseSessionId = entry.exerciseSessionId,
                                        setIndex = entry.setIndex,
                                        repsActual = entry.repsActual,
                                        loadActual = entry.loadActual,
                                        notes = entry.notes,
                                        skipped = entry.skipped,
                                    )
                                }).sortedBy { it.setIndex },
                            )
                        }
                    },
                )
            },
        )
    }

    override fun observeSessionDetail(sessionId: Long): Flow<WorkoutSessionDetail?> = detailFlow

    override suspend fun getSessionDetail(sessionId: Long): WorkoutSessionDetail? = detailFlow.value

    override fun observeCompletedSessions(): Flow<List<CompletedSessionListItem>> = flowOf(emptyList())

    override suspend fun getLatestCompletedSetEntry(exerciseTemplateId: Long, setIndex: Int): SetEntry? {
        return SetEntry(
            id = 1,
            exerciseSessionId = 999,
            setIndex = setIndex,
            repsActual = 8,
            loadActual = 30.0,
            notes = "",
            skipped = false,
        )
    }
}
