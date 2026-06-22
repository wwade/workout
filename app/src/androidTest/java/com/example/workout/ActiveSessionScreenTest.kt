package dev.wwade.workout

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performImeAction
import dev.wwade.workout.domain.model.LoadUnit
import dev.wwade.workout.ui.screen.ActiveSessionScreen
import dev.wwade.workout.ui.state.ActiveExerciseCardState
import dev.wwade.workout.ui.state.ActiveExerciseHistoryDialogState
import dev.wwade.workout.ui.state.ActiveExerciseHistoryRowState
import dev.wwade.workout.ui.state.ActiveSessionState
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
                onShowExerciseHistory = {},
                onDismissExerciseHistory = {},
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
                onShowExerciseHistory = {},
                onDismissExerciseHistory = {},
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

    @Test
    fun currentRoundDraftSurvivesBackAndForwardNavigation() {
        var currentSetIndex by mutableStateOf(1)
        var rounds by mutableStateOf(
            mapOf(
                0 to activeSessionState(currentSetIndex = 0),
                1 to activeSessionState(currentSetIndex = 1),
            ),
        )

        fun updateRound(setIndex: Int, transform: ActiveSessionState.() -> ActiveSessionState) {
            rounds = rounds + (setIndex to rounds.getValue(setIndex).transform())
        }

        composeRule.setContent {
            ActiveSessionScreen(
                state = rounds.getValue(currentSetIndex),
                onBack = {},
                onUpdateReps = { exerciseId, value ->
                    updateRound(currentSetIndex) {
                        updateExercise(exerciseId) { copy(repsInput = value) }
                    }
                },
                onUpdateLoad = { exerciseId, value ->
                    updateRound(currentSetIndex) {
                        updateExercise(exerciseId) { copy(loadInput = value) }
                    }
                },
                onUpdateNotes = { exerciseId, value ->
                    updateRound(currentSetIndex) {
                        updateExercise(exerciseId) { copy(notesInput = value) }
                    }
                },
                onUpdateSkipped = { exerciseId, value ->
                    updateRound(currentSetIndex) {
                        updateExercise(exerciseId) { copy(skipped = value) }
                    }
                },
                onPreviousRound = { currentSetIndex = 0 },
                onNextRound = { currentSetIndex = 1 },
                onSelectRound = { _, setIndex -> currentSetIndex = setIndex },
                onSaveRound = {},
                onAbandonSession = {},
                onShowExerciseHistory = {},
                onDismissExerciseHistory = {},
            )
        }

        composeRule.onNodeWithTag("reps-1").performTextReplacement("11")
        composeRule.onNodeWithTag("load-1").performTextReplacement("35")
        composeRule.onNodeWithTag("reps-1").assertTextContains("11")
        composeRule.onNodeWithTag("load-1").assertTextContains("35")

        composeRule.runOnUiThread { currentSetIndex = 0 }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reps-1").assertTextContains("8")
        composeRule.onNodeWithTag("load-1").assertTextContains("30")

        composeRule.runOnUiThread { currentSetIndex = 1 }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reps-1").assertTextContains("11")
        composeRule.onNodeWithTag("load-1").assertTextContains("35")
    }

    @Test
    fun currentUnsavedRoundCanSaveWhenValuesMatchPrefill() {
        var state by mutableStateOf(
            activeSessionState(currentSetIndex = 1).copy(
                currentCircuitName = "Cycle 2",
                positionOptions = listOf(
                    dev.wwade.workout.ui.state.SessionPositionOptionState(0, 0, "Cycle 1 - Set 1", "Saved", true),
                    dev.wwade.workout.ui.state.SessionPositionOptionState(0, 1, "Cycle 2 - Set 1", "Current", true),
                    dev.wwade.workout.ui.state.SessionPositionOptionState(0, 2, "Cycle 2 - Set 2", "Upcoming", false),
                ),
                exerciseCards = listOf(
                    exerciseCard(
                        exerciseSessionId = 1,
                        exerciseName = "Exercise 1",
                        repsInput = "8",
                        loadInput = "30",
                    ),
                ),
                canSaveRound = true,
            ),
        )

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
                onShowExerciseHistory = {},
                onDismissExerciseHistory = {},
            )
        }

        composeRule.onNodeWithText("Save round").assertIsEnabled()
        composeRule.onNodeWithTag("reps-1").assertTextContains("8")
        composeRule.onNodeWithTag("load-1").assertTextContains("30")
    }

    @Test
    fun previousWorkoutHistoryAndMoreHistoryDialogAreShown() {
        var state by mutableStateOf(
            activeSessionState(currentSetIndex = 0).copy(
                exerciseCards = listOf(
                    exerciseCard(
                        exerciseSessionId = 1,
                        exerciseName = "Exercise 1",
                        repsInput = "8",
                        loadInput = "30",
                    ).copy(
                        previousWorkoutHistory = listOf(
                            historyRow(setLabel = "Set 1", resultLabel = "8 reps - 30 lb"),
                            historyRow(setLabel = "Set 2", resultLabel = "7 reps - 30 lb"),
                        ),
                        fullHistory = listOf(
                            historyRow(setLabel = "Set 1", resultLabel = "8 reps - 30 lb"),
                            historyRow(
                                workoutName = "Older workout",
                                completedAtLabel = "Jun 1, 7:30 AM",
                                setLabel = "Set 1",
                                resultLabel = "6 reps - 25 lb",
                            ),
                        ),
                    ),
                ),
            ),
        )

        composeRule.setContent {
            ActiveSessionScreen(
                state = state,
                onBack = {},
                onUpdateReps = { _, _ -> },
                onUpdateLoad = { _, _ -> },
                onUpdateNotes = { _, _ -> },
                onUpdateSkipped = { _, _ -> },
                onPreviousRound = {},
                onNextRound = {},
                onSelectRound = { _, _ -> },
                onSaveRound = {},
                onAbandonSession = {},
                onShowExerciseHistory = { exerciseSessionId ->
                    val exercise = state.exerciseCards.single { it.exerciseSessionId == exerciseSessionId }
                    state = state.copy(
                        historyDialog = ActiveExerciseHistoryDialogState(
                            exerciseSessionId = exercise.exerciseSessionId,
                            exerciseName = exercise.exerciseName,
                            rows = exercise.fullHistory,
                        ),
                    )
                },
                onDismissExerciseHistory = {
                    state = state.copy(historyDialog = null)
                },
            )
        }

        composeRule.onNodeWithTag("history-preview-1").assertIsDisplayed()
        composeRule.onNodeWithText("Previous workout").assertIsDisplayed()
        composeRule.onNodeWithText("8 reps - 30 lb").assertIsDisplayed()
        composeRule.onNodeWithText("7 reps - 30 lb").assertIsDisplayed()

        composeRule.onNodeWithTag("history-more-1").performClick()
        composeRule.onNodeWithText("Exercise 1 history").assertIsDisplayed()
        composeRule.onNodeWithText("Older workout", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("6 reps - 25 lb").assertIsDisplayed()
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
                dev.wwade.workout.ui.state.SessionPositionOptionState(0, 0, "Warmup - Set 1", "Saved", true),
                dev.wwade.workout.ui.state.SessionPositionOptionState(0, 1, "Warmup - Set 2", "Current", true),
                dev.wwade.workout.ui.state.SessionPositionOptionState(0, 2, "Warmup - Set 3", "Upcoming", false),
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
            exerciseDefinitionId = exerciseSessionId + 100,
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

    private fun historyRow(
        workoutName: String = "Previous workout",
        completedAtLabel: String = "Jun 14, 9:15 AM",
        setLabel: String,
        resultLabel: String,
    ): ActiveExerciseHistoryRowState {
        return ActiveExerciseHistoryRowState(
            workoutSessionId = 1,
            workoutName = workoutName,
            completedAtLabel = completedAtLabel,
            setLabel = setLabel,
            resultLabel = resultLabel,
            notes = "",
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
