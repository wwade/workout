package dev.wwade.workout.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wwade.workout.domain.repository.SessionRepository
import dev.wwade.workout.ui.state.HistoryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val selectedSessionIds = MutableStateFlow<Set<Long>>(emptySet())
    private val pendingDeleteCount = MutableStateFlow(0)
    private val completedSessions = sessionRepository.observeCompletedSessions()
        .onEach { sessions ->
            val visibleSessionIds = sessions.map { it.sessionId }.toSet()
            selectedSessionIds.update { selected -> selected.intersect(visibleSessionIds) }
        }

    val state: StateFlow<HistoryState> = combine(
        completedSessions,
        selectedSessionIds,
        pendingDeleteCount,
    ) { sessions, selectedIds, deleteCount ->
        HistoryState(
            sessions = sessions,
            selectedSessionIds = selectedIds,
            isSelectionMode = selectedIds.isNotEmpty(),
            pendingDeleteCount = deleteCount,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryState(),
        )

    fun selectSession(sessionId: Long) {
        selectedSessionIds.update { it + sessionId }
    }

    fun toggleSessionSelection(sessionId: Long) {
        selectedSessionIds.update { selected ->
            if (sessionId in selected) selected - sessionId else selected + sessionId
        }
    }

    fun clearSelection() {
        selectedSessionIds.value = emptySet()
        pendingDeleteCount.value = 0
    }

    fun requestDeleteSelected() {
        val count = selectedSessionIds.value.size
        if (count > 0) {
            pendingDeleteCount.value = count
        }
    }

    fun cancelDelete() {
        pendingDeleteCount.value = 0
    }

    fun confirmDelete() {
        val sessionIds = selectedSessionIds.value
        if (sessionIds.isEmpty()) {
            pendingDeleteCount.value = 0
            return
        }

        viewModelScope.launch {
            sessionRepository.deleteCompletedSessions(sessionIds)
            selectedSessionIds.value = emptySet()
            pendingDeleteCount.value = 0
        }
    }
}
