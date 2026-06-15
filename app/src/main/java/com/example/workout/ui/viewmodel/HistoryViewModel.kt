package com.example.workout.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workout.domain.repository.SessionRepository
import com.example.workout.ui.state.HistoryState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(
    sessionRepository: SessionRepository,
) : ViewModel() {
    val state: StateFlow<HistoryState> = sessionRepository.observeCompletedSessions()
        .map { HistoryState(sessions = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryState(),
        )
}
