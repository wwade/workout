package dev.wwade.workout

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
                onDeleteWorkout = {},
                onStartWorkout = {},
                onResumeWorkout = {},
                onOpenHistory = {},
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
}
