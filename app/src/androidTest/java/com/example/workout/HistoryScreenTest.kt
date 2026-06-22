package dev.wwade.workout

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import dev.wwade.workout.domain.model.CompletedSessionListItem
import dev.wwade.workout.ui.screen.HistoryScreen
import dev.wwade.workout.ui.state.HistoryState
import java.time.LocalDateTime
import java.time.ZoneId
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

    @Test
    fun historyRowsShowStartedTimeDurationAndCircuits() {
        composeRule.setContent {
            HistoryScreen(
                state = HistoryState(
                    sessions = listOf(
                        historyItem(
                            sessionId = 1,
                            startedAt = localMillis(2026, 6, 21, 13, 32),
                            completedAt = localMillis(2026, 6, 21, 14, 37, 24),
                            circuitCount = 6,
                        ),
                    ),
                ),
                onBack = {},
                onOpenSession = {},
                onSelectSession = {},
                onToggleSessionSelection = {},
                onClearSelection = {},
                onRequestDeleteSelected = {},
                onCancelDelete = {},
                onConfirmDelete = {},
            )
        }

        composeRule.onNodeWithText("Started Jun 21, 2026 1:32 PM").assertIsDisplayed()
        composeRule.onNodeWithText("65 minutes").assertIsDisplayed()
        composeRule.onNodeWithText("6 circuits").assertIsDisplayed()
        composeRule.onAllNodesWithText("Completed Jun 21, 2026 2:37 PM").assertCountEquals(0)
    }
}

private fun historyItem(
    sessionId: Long,
    startedAt: Long = sessionId,
    completedAt: Long = sessionId,
    circuitCount: Int = 1,
): CompletedSessionListItem {
    return CompletedSessionListItem(
        sessionId = sessionId,
        workoutName = "Workout $sessionId",
        startedAt = startedAt,
        completedAt = completedAt,
        circuitCount = circuitCount,
    )
}

private fun localMillis(
    year: Int,
    month: Int,
    dayOfMonth: Int,
    hour: Int,
    minute: Int,
    second: Int = 0,
): Long {
    return LocalDateTime.of(year, month, dayOfMonth, hour, minute, second)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
