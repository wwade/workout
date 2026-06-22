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
import dev.wwade.workout.domain.model.WorkoutListItem
import dev.wwade.workout.ui.screen.WorkoutListScreen
import dev.wwade.workout.ui.state.WorkoutListState
import org.junit.Rule
import org.junit.Test

class WorkoutListScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsEmptyStateMessage() {
        composeRule.setContent {
            WorkoutListScreen(
                state = WorkoutListState(),
                onCreateWorkout = {},
                onEditWorkout = {},
                onSelectWorkout = {},
                onToggleWorkoutSelection = {},
                onClearSelection = {},
                onRequestDeleteSelected = {},
                onCancelDelete = {},
                onConfirmDelete = {},
                onStartWorkout = {},
                onResumeWorkout = {},
                onOpenHistory = {},
                onOpenExerciseLibrary = {},
                onPrepareExport = { null },
                onExportSaved = {},
                onExportFailed = {},
                onExportCancelled = {},
                onShowImportOptions = {},
                onHideImportDialog = {},
                onShowUrlImport = {},
                onUpdateImportUrl = {},
                onImportFromUrl = {},
                onImportFromJson = {},
                onImportFileReadFailed = {},
                onDismissImportMessage = {},
            )
        }

        composeRule.onNodeWithText("No workouts yet").assertIsDisplayed()
    }

    @Test
    fun longPressSelectsRowsAndConfirmDelete() {
        var deletedWorkoutIds = emptySet<Long>()

        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    WorkoutListState(
                        workouts = listOf(workoutItem(1), workoutItem(2)),
                    ),
                )
            }

            WorkoutListScreen(
                state = state,
                onCreateWorkout = {},
                onEditWorkout = {},
                onSelectWorkout = { workoutId ->
                    val selected = state.selectedWorkoutIds + workoutId
                    state = state.copy(
                        selectedWorkoutIds = selected,
                        isSelectionMode = selected.isNotEmpty(),
                    )
                },
                onToggleWorkoutSelection = { workoutId ->
                    val selected = if (workoutId in state.selectedWorkoutIds) {
                        state.selectedWorkoutIds - workoutId
                    } else {
                        state.selectedWorkoutIds + workoutId
                    }
                    state = state.copy(
                        selectedWorkoutIds = selected,
                        isSelectionMode = selected.isNotEmpty(),
                    )
                },
                onClearSelection = {
                    state = state.copy(
                        selectedWorkoutIds = emptySet(),
                        isSelectionMode = false,
                        pendingDeleteCount = 0,
                    )
                },
                onRequestDeleteSelected = {
                    state = state.copy(pendingDeleteCount = state.selectedWorkoutIds.size)
                },
                onCancelDelete = {
                    state = state.copy(pendingDeleteCount = 0)
                },
                onConfirmDelete = {
                    deletedWorkoutIds = state.selectedWorkoutIds
                    state = state.copy(
                        selectedWorkoutIds = emptySet(),
                        isSelectionMode = false,
                        pendingDeleteCount = 0,
                    )
                },
                onStartWorkout = {},
                onResumeWorkout = {},
                onOpenHistory = {},
                onOpenExerciseLibrary = {},
                onPrepareExport = { null },
                onExportSaved = {},
                onExportFailed = {},
                onExportCancelled = {},
                onShowImportOptions = {},
                onHideImportDialog = {},
                onShowUrlImport = {},
                onUpdateImportUrl = {},
                onImportFromUrl = {},
                onImportFromJson = {},
                onImportFileReadFailed = {},
                onDismissImportMessage = {},
            )
        }

        composeRule.onNodeWithTag("workout-row-1").performTouchInput { longClick() }
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeRule.onNodeWithTag("workout-checkbox-1").assertIsOn()

        composeRule.onNodeWithTag("workout-row-2").performClick()
        composeRule.onNodeWithText("2 selected").assertIsDisplayed()
        composeRule.onNodeWithTag("workout-checkbox-2").assertIsOn()

        composeRule.onNodeWithContentDescription("Delete selected workouts").performClick()
        composeRule.onNodeWithText("Delete workouts?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.runOnIdle {
            assertThat(deletedWorkoutIds).containsExactly(1L, 2L)
        }
    }
}

private fun workoutItem(id: Long): WorkoutListItem {
    return WorkoutListItem(
        id = id,
        name = "Workout $id",
        circuitCount = 1,
        exerciseCount = 2,
        updatedAt = id,
    )
}
