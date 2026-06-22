package dev.wwade.workout

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import dev.wwade.workout.domain.model.CompletedSessionListItem
import dev.wwade.workout.ui.screen.HistoryScreen
import dev.wwade.workout.ui.state.HistoryState
import org.junit.Rule
import org.junit.Test

class HistoryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longPressSelectsRowsAndConfirmDelete() {
        var deletedSessionIds = emptySet<Long>()

        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    HistoryState(
                        sessions = listOf(historyItem(1), historyItem(2)),
                    ),
                )
            }

            HistoryScreen(
                state = state,
                onBack = {},
                onOpenSession = {},
                onSelectSession = { sessionId ->
                    val selected = state.selectedSessionIds + sessionId
                    state = state.copy(
                        selectedSessionIds = selected,
                        isSelectionMode = selected.isNotEmpty(),
                    )
                },
                onToggleSessionSelection = { sessionId ->
                    val selected = if (sessionId in state.selectedSessionIds) {
                        state.selectedSessionIds - sessionId
                    } else {
                        state.selectedSessionIds + sessionId
                    }
                    state = state.copy(
                        selectedSessionIds = selected,
                        isSelectionMode = selected.isNotEmpty(),
                    )
                },
                onClearSelection = {
                    state = state.copy(
                        selectedSessionIds = emptySet(),
                        isSelectionMode = false,
                        pendingDeleteCount = 0,
                    )
                },
                onRequestDeleteSelected = {
                    state = state.copy(pendingDeleteCount = state.selectedSessionIds.size)
                },
                onCancelDelete = {
                    state = state.copy(pendingDeleteCount = 0)
                },
                onConfirmDelete = {
                    deletedSessionIds = state.selectedSessionIds
                    state = state.copy(
                        selectedSessionIds = emptySet(),
                        isSelectionMode = false,
                        pendingDeleteCount = 0,
                    )
                },
            )
        }

        composeRule.onNodeWithTag("history-row-1").performTouchInput { longClick() }
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeRule.onNodeWithTag("history-checkbox-1").assertIsOn()

        composeRule.onNodeWithTag("history-row-2").performClick()
        composeRule.onNodeWithText("2 selected").assertIsDisplayed()
        composeRule.onNodeWithTag("history-checkbox-2").assertIsOn()

        composeRule.onNodeWithContentDescription("Delete selected history").performClick()
        composeRule.onNodeWithText("Delete history?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.runOnIdle {
            assertThat(deletedSessionIds).containsExactly(1L, 2L)
        }
    }
}

private fun historyItem(sessionId: Long): CompletedSessionListItem {
    return CompletedSessionListItem(
        sessionId = sessionId,
        workoutName = "Workout $sessionId",
        completedAt = sessionId,
        circuitCount = 1,
    )
}
