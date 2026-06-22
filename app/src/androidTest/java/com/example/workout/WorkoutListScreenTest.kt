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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import dev.wwade.workout.domain.model.ActiveSessionSummary
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

    @Test
    fun activeSessionShowsGlobalResumeAndHidesRowActions() {
        var resumedSessionId: Long? = null

        composeRule.setContent {
            WorkoutListScreen(
                state = WorkoutListState(
                    workouts = listOf(workoutItem(1), workoutItem(2)),
                    activeSession = ActiveSessionSummary(
                        sessionId = 99,
                        workoutTemplateId = 1,
                        workoutName = "Workout 1",
                    ),
                ),
                onCreateWorkout = {},
                onEditWorkout = {},
                onSelectWorkout = {},
                onToggleWorkoutSelection = {},
                onClearSelection = {},
                onRequestDeleteSelected = {},
                onCancelDelete = {},
                onConfirmDelete = {},
                onStartWorkout = {},
                onResumeWorkout = { resumedSessionId = it },
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

        composeRule.onNodeWithTag("active-session-banner").assertIsDisplayed()
        composeRule.onNodeWithText("Workout in progress: Workout 1").assertIsDisplayed()
        composeRule.onAllNodesWithText("Resume").assertCountEquals(1)
        composeRule.onAllNodesWithText("Start").assertCountEquals(0)

        composeRule.onNodeWithTag("resume-active-session").performClick()

        composeRule.runOnIdle {
            assertThat(resumedSessionId).isEqualTo(99)
        }
    }

    @Test
    fun orphanedActiveSessionStillShowsGlobalResume() {
        var resumedSessionId: Long? = null

        composeRule.setContent {
            WorkoutListScreen(
                state = WorkoutListState(
                    workouts = listOf(workoutItem(1), workoutItem(2)),
                    activeSession = ActiveSessionSummary(
                        sessionId = 77,
                        workoutTemplateId = 100,
                        workoutName = "Deleted workout",
                    ),
                ),
                onCreateWorkout = {},
                onEditWorkout = {},
                onSelectWorkout = {},
                onToggleWorkoutSelection = {},
                onClearSelection = {},
                onRequestDeleteSelected = {},
                onCancelDelete = {},
                onConfirmDelete = {},
                onStartWorkout = {},
                onResumeWorkout = { resumedSessionId = it },
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

        composeRule.onNodeWithText("Workout in progress: Deleted workout").assertIsDisplayed()
        composeRule.onNodeWithTag("resume-active-session").performClick()

        composeRule.runOnIdle {
            assertThat(resumedSessionId).isEqualTo(77)
        }
    }

    @Test
    fun rowStartActionsOnlyShowWithoutActiveSession() {
        composeRule.setContent {
            WorkoutListScreen(
                state = WorkoutListState(workouts = listOf(workoutItem(1), workoutItem(2))),
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

        composeRule.onAllNodesWithTag("active-session-banner").assertCountEquals(0)
        composeRule.onAllNodesWithText("Start").assertCountEquals(2)
        composeRule.onAllNodesWithText("Resume").assertCountEquals(0)
    }

    @Test
    fun editingActiveTemplateShowsWarningBeforeProceeding() {
        var editedWorkoutId: Long? = null

        composeRule.setContent {
            WorkoutListScreen(
                state = WorkoutListState(
                    workouts = listOf(workoutItem(1), workoutItem(2)),
                    activeSession = ActiveSessionSummary(
                        sessionId = 99,
                        workoutTemplateId = 1,
                        workoutName = "Workout 1",
                    ),
                ),
                onCreateWorkout = {},
                onEditWorkout = { editedWorkoutId = it },
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

        composeRule.onNodeWithTag("edit-workout-1").performClick()
        composeRule.onNodeWithText("Edit workout?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Editing this workout will not affect the workout currently in progress. " +
                "Changes will apply the next time you start it.",
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            assertThat(editedWorkoutId).isNull()
        }

        composeRule.onNodeWithText("Edit").performClick()

        composeRule.runOnIdle {
            assertThat(editedWorkoutId).isEqualTo(1)
        }
    }

    @Test
    fun editingInactiveTemplateProceedsWithoutWarning() {
        var editedWorkoutId: Long? = null

        composeRule.setContent {
            WorkoutListScreen(
                state = WorkoutListState(
                    workouts = listOf(workoutItem(1), workoutItem(2)),
                    activeSession = ActiveSessionSummary(
                        sessionId = 99,
                        workoutTemplateId = 1,
                        workoutName = "Workout 1",
                    ),
                ),
                onCreateWorkout = {},
                onEditWorkout = { editedWorkoutId = it },
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

        composeRule.onNodeWithTag("edit-workout-2").performClick()

        composeRule.onAllNodesWithText("Edit workout?").assertCountEquals(0)
        composeRule.runOnIdle {
            assertThat(editedWorkoutId).isEqualTo(2)
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
