package com.example.workout

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performImeAction
import com.example.workout.domain.model.LoadUnit
import com.example.workout.ui.screen.ActiveSessionScreen
import com.example.workout.ui.state.ActiveExerciseCardState
import com.example.workout.ui.state.ActiveSessionState
import org.junit.Rule
import org.junit.Test

class ActiveSessionScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nextFlowKeepsPrefillAndResetsFocusOnNewRound() {
        var state by mutableStateOf(activeSessionState(currentSetIndex = 0))

        composeRule.setContent {
            ActiveSessionScreen(
                state = state,
                onBack = {},
                onUpdateReps = { exerciseId, value ->
                    state = state.updateExercise(exerciseId) { copy(repsInput = value) }
                },
                onUpdateLoad = { exerciseId, value ->
                    state = state.updateExercise(exerciseId) { copy(loadInput = value) }
                },
                onUpdateNotes = { exerciseId, value ->
                    state = state.updateExercise(exerciseId) { copy(notesInput = value) }
                },
                onUpdateSkipped = { exerciseId, value ->
                    state = state.updateExercise(exerciseId) { copy(skipped = value) }
                },
                onPreviousRound = {},
                onNextRound = {},
                onSelectRound = { _, _ -> },
                onSaveRound = {},
                onAbandonSession = {},
            )
        }

        composeRule.onNodeWithTag("reps-1").assertIsFocused().assertTextContains("8")
        composeRule.onNodeWithTag("reps-1").performImeAction()
        composeRule.onNodeWithTag("load-1").assertIsFocused().assertTextContains("30")
        composeRule.onNodeWithTag("load-1").performImeAction()
        composeRule.onNodeWithTag("reps-2").assertIsFocused().assertTextContains("10")
        composeRule.onNodeWithTag("load-1").assertTextContains("30")
        composeRule.onNodeWithTag("reps-2").performImeAction()
        composeRule.onNodeWithTag("load-2").assertIsFocused().assertTextContains("40")
        composeRule.onNodeWithTag("load-2").performImeAction()
        composeRule.onNodeWithTag("load-1").assertTextContains("30")
        composeRule.onNodeWithTag("load-2").assertTextContains("40")

        composeRule.runOnUiThread {
            state = activeSessionState(currentSetIndex = 1)
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reps-1").assertIsFocused().assertTextContains("8")
        composeRule.onNodeWithTag("load-1").assertTextContains("30")
    }

    @Test
    fun newRoundScrollsBackToTop() {
        var state by mutableStateOf(activeSessionState(currentSetIndex = 0, exerciseCount = 12))

        composeRule.setContent {
            ActiveSessionScreen(
                state = state,
                onBack = {},
                onUpdateReps = { exerciseId, value ->
                    state = state.updateExercise(exerciseId) { copy(repsInput = value) }
                },
                onUpdateLoad = { exerciseId, value ->
                    state = state.updateExercise(exerciseId) { copy(loadInput = value) }
                },
                onUpdateNotes = { exerciseId, value ->
                    state = state.updateExercise(exerciseId) { copy(notesInput = value) }
                },
                onUpdateSkipped = { exerciseId, value ->
                    state = state.updateExercise(exerciseId) { copy(skipped = value) }
                },
                onPreviousRound = {},
                onNextRound = {},
                onSelectRound = { _, _ -> },
                onSaveRound = {},
                onAbandonSession = {},
            )
        }

        composeRule.onNodeWithTag("exercise-list").performScrollToIndex(11)
        composeRule.onNodeWithTag("reps-12").assertIsDisplayed()

        composeRule.runOnUiThread {
            state = activeSessionState(currentSetIndex = 1, exerciseCount = 12)
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reps-1").assertIsDisplayed().assertIsFocused()
    }

    private fun activeSessionState(currentSetIndex: Int, exerciseCount: Int = 2): ActiveSessionState {
        return ActiveSessionState(
            sessionId = 1,
            workoutName = "Part one",
            currentCircuitIndex = 0,
            currentCircuitName = "Warmup",
            currentSetIndex = currentSetIndex,
            totalSetsInCircuit = 3,
            canSaveRound = true,
            canGoBack = currentSetIndex > 0,
            canGoForward = currentSetIndex < 2,
            positionOptions = listOf(
                com.example.workout.ui.state.SessionPositionOptionState(0, 0, "Warmup - Set 1", "Saved", true),
                com.example.workout.ui.state.SessionPositionOptionState(0, 1, "Warmup - Set 2", "Current", true),
                com.example.workout.ui.state.SessionPositionOptionState(0, 2, "Warmup - Set 3", "Upcoming", false),
            ),
            exerciseCards = (1..exerciseCount).map { index ->
                exerciseCard(
                    exerciseSessionId = index.toLong(),
                    exerciseName = "Exercise $index",
                    repsInput = if (index == 1) "8" else "10",
                    loadInput = if (index == 1) "30" else "40",
                )
            },
        )
    }

    private fun exerciseCard(
        exerciseSessionId: Long,
        exerciseName: String,
        repsInput: String,
        loadInput: String,
    ): ActiveExerciseCardState {
        return ActiveExerciseCardState(
            exerciseSessionId = exerciseSessionId,
            exerciseTemplateId = exerciseSessionId,
            exerciseName = exerciseName,
            guidance = "",
            repRangeLabel = "6-8",
            loadRangeLabel = "30-60 sec",
            loadUnit = LoadUnit.SEC,
            restTimeSeconds = 60,
            repsInput = repsInput,
            loadInput = loadInput,
            notesInput = "",
            skipped = false,
        )
    }

    private fun ActiveSessionState.updateExercise(
        exerciseId: Long,
        transform: ActiveExerciseCardState.() -> ActiveExerciseCardState,
    ): ActiveSessionState {
        return copy(
            exerciseCards = exerciseCards.map { exercise ->
                if (exercise.exerciseSessionId == exerciseId) {
                    exercise.transform()
                } else {
                    exercise
                }
            },
        )
    }
}
