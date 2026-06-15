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
}

private class FakeSessionRepository(
    detail: WorkoutSessionDetail,
) : SessionRepository {
    private val detailFlow = MutableStateFlow(detail)

    override fun observeActiveSessionId(): Flow<Long?> = flowOf(detailFlow.value.sessionId)

    override suspend fun startWorkout(workoutId: Long): Long = error("Not needed in test")

    override suspend fun abandonSession(sessionId: Long) = Unit

    override suspend fun saveRoundEntries(sessionId: Long, entries: List<SetEntryDraft>) = Unit

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
