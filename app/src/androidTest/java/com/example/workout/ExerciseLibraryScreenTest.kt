package dev.wwade.workout

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.wwade.workout.domain.model.ExerciseDefinition
import dev.wwade.workout.ui.screen.ExerciseLibraryScreen
import dev.wwade.workout.ui.state.ExerciseLibraryState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class ExerciseLibraryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun exercisesCollapseExpandAndBulkToggleVisibleItems() {
        var searchQuery by mutableStateOf("")
        var archivedExercise: Pair<Long, Boolean>? = null
        val exercises = listOf(
            exerciseDefinition(
                id = 1,
                name = "Goblet Squat",
                guidance = "Stay tall",
                usageCount = 2,
            ),
            exerciseDefinition(
                id = 2,
                name = "Push-Up",
                guidance = "Brace core",
                usageCount = 1,
            ),
        )

        composeRule.setContent {
            ExerciseLibraryScreen(
                state = ExerciseLibraryState(
                    exercises = exercises,
                    searchQuery = searchQuery,
                ),
                onBack = {},
                onUpdateSearch = { searchQuery = it },
                onSaveExercise = { true },
                onArchiveExercise = { id, archived -> archivedExercise = id to archived },
                onDismissError = {},
            )
        }

        composeRule.onAllNodesWithText("Stay tall").assertCountEquals(0)
        composeRule.onAllNodesWithText("Brace core").assertCountEquals(0)

        composeRule.onNodeWithText("Goblet Squat").performClick()
        composeRule.onNodeWithText("Stay tall").assertIsDisplayed()
        composeRule.onAllNodesWithText("Brace core").assertCountEquals(0)

        composeRule.onNodeWithText("Goblet Squat").performClick()
        composeRule.onAllNodesWithText("Stay tall").assertCountEquals(0)

        composeRule.onNodeWithText("Expand all").performClick()
        composeRule.onNodeWithText("Stay tall").assertIsDisplayed()
        composeRule.onNodeWithText("Brace core").assertIsDisplayed()
        composeRule.onNodeWithText("Collapse all").assertIsDisplayed()

        composeRule.onNodeWithText("Collapse all").performClick()
        composeRule.onAllNodesWithText("Stay tall").assertCountEquals(0)
        composeRule.onAllNodesWithText("Brace core").assertCountEquals(0)

        composeRule.onNodeWithTag("exercise-search").performTextInput("Push")
        composeRule.onNodeWithText("Expand all").performClick()
        composeRule.onNodeWithText("Brace core").assertIsDisplayed()
        composeRule.onAllNodesWithText("Stay tall").assertCountEquals(0)

        composeRule.onNodeWithTag("exercise-search").performTextClearance()
        composeRule.onNodeWithText("Brace core").assertIsDisplayed()
        composeRule.onAllNodesWithText("Stay tall").assertCountEquals(0)

        composeRule.onAllNodesWithContentDescription("Edit")[0].performClick()
        composeRule.onNodeWithText("Edit Exercise").assertIsDisplayed()

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onAllNodesWithContentDescription("Archive")[0].performClick()
        composeRule.waitForIdle()
        assertThat(archivedExercise).isEqualTo(1L to true)
    }

    private fun exerciseDefinition(
        id: Long,
        name: String,
        guidance: String,
        usageCount: Int,
    ) = ExerciseDefinition(
        id = id,
        name = name,
        normalizedName = name.lowercase(),
        defaultGuidance = guidance,
        archived = false,
        createdAt = 1,
        updatedAt = 1,
        usageCount = usageCount,
    )
}
