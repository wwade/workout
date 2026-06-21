package dev.wwade.workout

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.wwade.workout.data.db.AppDatabase
import dev.wwade.workout.data.repository.RoomSessionRepository
import dev.wwade.workout.data.repository.RoomWorkoutRepository
import dev.wwade.workout.domain.exporter.ExportWorkoutDataUseCase
import dev.wwade.workout.domain.exporter.WorkoutDataExportSnapshot
import dev.wwade.workout.domain.importer.ImportWorkoutsUseCase
import dev.wwade.workout.domain.importer.WorkoutImportJsonFetcher
import dev.wwade.workout.domain.repository.WorkoutDataExportRepository
import dev.wwade.workout.ui.screen.WorkoutListScreen
import dev.wwade.workout.ui.theme.WorkoutTheme
import dev.wwade.workout.ui.viewmodel.WorkoutListViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutImportDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: AppDatabase
    private lateinit var workoutRepository: RoomWorkoutRepository
    private lateinit var sessionRepository: RoomSessionRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workoutRepository = RoomWorkoutRepository(database.workoutTemplateDao())
        sessionRepository = RoomSessionRepository(
            workoutTemplateDao = database.workoutTemplateDao(),
            workoutSessionDao = database.workoutSessionDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun urlImportAddsWorkoutTemplateToList() {
        val fetcher = StaticWorkoutImportFetcher(importPayload)
        val viewModel = WorkoutListViewModel(
            workoutRepository = workoutRepository,
            sessionRepository = sessionRepository,
            importWorkoutsUseCase = ImportWorkoutsUseCase(
                workoutRepository = workoutRepository,
                jsonFetcher = fetcher,
            ),
            exportWorkoutDataUseCase = ExportWorkoutDataUseCase(
                exportRepository = object : WorkoutDataExportRepository {
                    override suspend fun exportSnapshot(): WorkoutDataExportSnapshot {
                        return WorkoutDataExportSnapshot(
                            workouts = emptyList(),
                            sessions = emptyList(),
                        )
                    }
                },
            ),
        )

        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            WorkoutTheme {
                WorkoutListScreen(
                    state = state,
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
                    onShowImportOptions = viewModel::showImportOptions,
                    onHideImportDialog = viewModel::hideImportDialog,
                    onShowUrlImport = viewModel::showUrlImport,
                    onUpdateImportUrl = viewModel::updateImportUrl,
                    onImportFromUrl = viewModel::importFromUrl,
                    onImportFromJson = viewModel::importFromJson,
                    onImportFileReadFailed = viewModel::showImportError,
                    onDismissImportMessage = viewModel::clearImportMessage,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Import workouts").performClick()
        composeRule.onNodeWithText("URL").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("https://example.com/import.yaml")
        composeRule.onNodeWithText("Import").performClick()

        composeRule.onNodeWithText("Imported Device Test Workout").assertIsDisplayed()
        composeRule.onNodeWithText("1 circuits | 1 exercises").assertIsDisplayed()
        composeRule.onNodeWithText("Imported 1 workout.").assertIsDisplayed()

        assertThat(fetcher.requestedUrl).isEqualTo("https://example.com/import.yaml")
    }

    private class StaticWorkoutImportFetcher(
        private val payload: String,
    ) : WorkoutImportJsonFetcher {
        var requestedUrl: String? = null
            private set

        override suspend fun fetch(url: String): String {
            requestedUrl = url
            return payload
        }
    }
}

private const val importPayload = """
workouts:
  - name: Imported Device Test Workout
    circuits:
      - name: Strength
        exercises:
          - name: Goblet Squat
            guidance: Keep chest tall.
            repMin: 8
            repMax: 10
            loadKind: WEIGHT
            loadMin: 25.0
            loadMax: 35.0
            loadUnit: LB
            restTimeSeconds: 75
            setCount: 3
"""
