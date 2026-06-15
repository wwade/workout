package com.example.workout

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.workout.ui.screen.WorkoutListScreen
import com.example.workout.ui.state.WorkoutListState
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
            )
        }

        composeRule.onNodeWithText("No workouts yet").assertIsDisplayed()
    }
}
