package dev.wwade.workout.ui.viewmodel

import dev.wwade.workout.domain.model.CompletedSessionListItem
import dev.wwade.workout.domain.model.ExerciseSetHistoryItem
import dev.wwade.workout.domain.model.SetEntry
import dev.wwade.workout.domain.model.SetEntryDraft
import dev.wwade.workout.domain.model.WorkoutSessionDetail
import dev.wwade.workout.domain.repository.SessionRepository
import dev.wwade.workout.util.MainDispatcherRule
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
class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun selectToggleAndClearSelectionUpdatesState() = runTest {
        val repository = FakeHistorySessionRepository()
        val viewModel = HistoryViewModel(repository)
        val collector = backgroundScope.launch { viewModel.state.collectLatest { } }

        viewModel.selectSession(1)
        viewModel.toggleSessionSelection(2)
        advanceUntilIdle()

        assertThat(viewModel.state.value.selectedSessionIds).containsExactly(1L, 2L)
        assertThat(viewModel.state.value.isSelectionMode).isTrue()

        viewModel.toggleSessionSelection(1)
        advanceUntilIdle()

        assertThat(viewModel.state.value.selectedSessionIds).containsExactly(2L)

        viewModel.clearSelection()
        advanceUntilIdle()

        assertThat(viewModel.state.value.selectedSessionIds).isEmpty()
        assertThat(viewModel.state.value.isSelectionMode).isFalse()
        collector.cancel()
    }

    @Test
    fun confirmDeleteCallsRepositoryAndClearsSelection() = runTest {
        val repository = FakeHistorySessionRepository()
        val viewModel = HistoryViewModel(repository)
        val collector = backgroundScope.launch { viewModel.state.collectLatest { } }

        viewModel.selectSession(1)
        viewModel.selectSession(2)
        viewModel.requestDeleteSelected()
        advanceUntilIdle()

        assertThat(viewModel.state.value.pendingDeleteCount).isEqualTo(2)

        viewModel.confirmDelete()
        advanceUntilIdle()

        assertThat(repository.deletedSessionIds).containsExactly(1L, 2L)
        assertThat(viewModel.state.value.selectedSessionIds).isEmpty()
        assertThat(viewModel.state.value.pendingDeleteCount).isEqualTo(0)
        collector.cancel()
    }

    @Test
    fun cancelDeleteKeepsSelection() = runTest {
        val repository = FakeHistorySessionRepository()
        val viewModel = HistoryViewModel(repository)
        val collector = backgroundScope.launch { viewModel.state.collectLatest { } }

        viewModel.selectSession(1)
        viewModel.requestDeleteSelected()
        viewModel.cancelDelete()
        advanceUntilIdle()

        assertThat(viewModel.state.value.selectedSessionIds).containsExactly(1L)
        assertThat(viewModel.state.value.pendingDeleteCount).isEqualTo(0)
        collector.cancel()
    }

    @Test
    fun staleSelectedSessionsAreRemovedWhenHistoryChanges() = runTest {
        val repository = FakeHistorySessionRepository()
        val viewModel = HistoryViewModel(repository)
        val collector = backgroundScope.launch { viewModel.state.collectLatest { } }

        viewModel.selectSession(1)
        viewModel.selectSession(2)
        advanceUntilIdle()

        repository.sessions.value = listOf(historyItem(2))
        advanceUntilIdle()

        assertThat(viewModel.state.value.selectedSessionIds).containsExactly(2L)
        collector.cancel()
    }
}

private class FakeHistorySessionRepository : SessionRepository {
    val sessions = MutableStateFlow(listOf(historyItem(1), historyItem(2)))
    val deletedSessionIds = mutableSetOf<Long>()

    override fun observeActiveSessionId(): Flow<Long?> = flowOf(null)

    override suspend fun startWorkout(workoutId: Long): Long = workoutId

    override suspend fun abandonSession(sessionId: Long) = Unit

    override suspend fun saveRoundEntries(sessionId: Long, entries: List<SetEntryDraft>) = Unit

    override fun observeSessionDetail(sessionId: Long): Flow<WorkoutSessionDetail?> = flowOf(null)

    override suspend fun getSessionDetail(sessionId: Long): WorkoutSessionDetail? = null

    override fun observeCompletedSessions(): Flow<List<CompletedSessionListItem>> = sessions

    override suspend fun deleteCompletedSessions(sessionIds: Set<Long>) {
        deletedSessionIds += sessionIds
    }

    override suspend fun getLatestCompletedSetEntry(exerciseDefinitionId: Long, setIndex: Int): SetEntry? = null

    override suspend fun getPreviousWorkoutSetEntries(
        exerciseDefinitionId: Long,
    ): List<ExerciseSetHistoryItem> = emptyList()

    override suspend fun getRecentCompletedSetEntries(
        exerciseDefinitionId: Long,
        limit: Int,
    ): List<ExerciseSetHistoryItem> = emptyList()
}

private fun historyItem(sessionId: Long): CompletedSessionListItem {
    return CompletedSessionListItem(
        sessionId = sessionId,
        workoutName = "Workout $sessionId",
        completedAt = sessionId,
        circuitCount = 1,
    )
}
