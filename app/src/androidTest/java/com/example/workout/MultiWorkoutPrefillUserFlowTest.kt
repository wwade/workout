package dev.wwade.workout

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.wwade.workout.data.db.AppDatabase
import dev.wwade.workout.data.repository.RoomExerciseDefinitionRepository
import dev.wwade.workout.data.repository.RoomSessionRepository
import dev.wwade.workout.data.repository.RoomWorkoutDataExportRepository
import dev.wwade.workout.data.repository.RoomWorkoutDataImportRepository
import dev.wwade.workout.data.repository.RoomWorkoutRepository
import dev.wwade.workout.domain.backup.BackupNowAfterWorkoutCompletionUseCase
import dev.wwade.workout.domain.backup.DriveBackupAccessTokenProvider
import dev.wwade.workout.domain.backup.DriveBackupRepository
import dev.wwade.workout.domain.backup.DriveBackupSettings
import dev.wwade.workout.domain.backup.DriveBackupSettingsRepository
import dev.wwade.workout.domain.backup.DriveBackupSnapshot
import dev.wwade.workout.domain.backup.ListDriveBackupSnapshotsUseCase
import dev.wwade.workout.domain.backup.RestoreDriveBackupUseCase
import dev.wwade.workout.domain.backup.SetDriveBackupEnabledUseCase
import dev.wwade.workout.domain.exporter.ExportWorkoutDataUseCase
import dev.wwade.workout.domain.importer.ImportWorkoutsUseCase
import dev.wwade.workout.domain.model.CircuitDraft
import dev.wwade.workout.domain.model.ExerciseDraft
import dev.wwade.workout.domain.model.SessionStatus
import dev.wwade.workout.domain.model.WorkoutDraft
import dev.wwade.workout.domain.repository.ExerciseDefinitionRepository
import dev.wwade.workout.domain.repository.SessionRepository
import dev.wwade.workout.domain.repository.WorkoutRepository
import dev.wwade.workout.ui.screen.ActiveSessionScreen
import dev.wwade.workout.ui.screen.SessionSummaryScreen
import dev.wwade.workout.ui.screen.WorkoutListScreen
import dev.wwade.workout.ui.theme.WorkoutTheme
import dev.wwade.workout.ui.viewmodel.ActiveSessionViewModel
import dev.wwade.workout.ui.viewmodel.SessionSummaryViewModel
import dev.wwade.workout.ui.viewmodel.WorkoutListViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiWorkoutPrefillUserFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: AppDatabase
    private lateinit var workoutRepository: RoomWorkoutRepository
    private lateinit var sessionRepository: RoomSessionRepository
    private lateinit var container: TestAppContainer

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
        container = TestAppContainer(database, workoutRepository, sessionRepository)
    }

    @After
    fun tearDown() {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {}
            composeRule.activity.viewModelStore.clear()
        }
        composeRule.waitForIdle()
        database.close()
    }

    @Test
    fun alternatingCompletedWorkoutsPrefillFromEachWorkoutHistory() {
        val workout1Id = runBlocking {
            workoutRepository.saveWorkout(singleSetWorkout("Workout 1", "Workout 1 press"))
        }
        val workout2Id = runBlocking {
            workoutRepository.saveWorkout(singleSetWorkout("Workout 2", "Workout 2 squat"))
        }

        composeRule.setContent {
            WorkoutTheme {
                WorkoutUserFlowTestHarness(container = container)
            }
        }

        completeWorkout(
            workoutId = workout1Id,
            workoutName = "Workout 1",
            expectedCompletedSessionCount = 1,
            reps = 8,
            load = "30",
            expectedPrefill = null,
        )
        completeWorkout(
            workoutId = workout1Id,
            workoutName = "Workout 1",
            expectedCompletedSessionCount = 2,
            reps = 8,
            load = "30",
            expectedPrefill = PrefillExpectation(reps = "8", load = "30"),
        )
        completeWorkout(
            workoutId = workout2Id,
            workoutName = "Workout 2",
            expectedCompletedSessionCount = 3,
            reps = 12,
            load = "55",
            expectedPrefill = null,
        )
        completeWorkout(
            workoutId = workout1Id,
            workoutName = "Workout 1",
            expectedCompletedSessionCount = 4,
            reps = 8,
            load = "30",
            expectedPrefill = PrefillExpectation(reps = "8", load = "30"),
        )
        completeWorkout(
            workoutId = workout2Id,
            workoutName = "Workout 2",
            expectedCompletedSessionCount = 5,
            reps = 12,
            load = "55",
            expectedPrefill = PrefillExpectation(reps = "12", load = "55"),
        )

        val sessions = runBlocking { database.workoutSessionDao().getAllSessionDetails() }
        assertThat(sessions).hasSize(5)
        assertThat(sessions.map { it.session.status }).containsExactly(
            SessionStatus.COMPLETED,
            SessionStatus.COMPLETED,
            SessionStatus.COMPLETED,
            SessionStatus.COMPLETED,
            SessionStatus.COMPLETED,
        )
        assertThat(runBlocking { database.workoutSessionDao().getActiveSessionId() }).isNull()
    }

    private fun completeWorkout(
        workoutId: Long,
        workoutName: String,
        expectedCompletedSessionCount: Int,
        reps: Int,
        load: String,
        expectedPrefill: PrefillExpectation?,
    ) {
        waitForDisplayed("workout-row-$workoutId")
        composeRule.onNodeWithTag("start-workout-$workoutId").performClick()

        val exerciseSessionId = waitForActiveExerciseSessionId()
        waitForTextDisplayed("Finish")
        composeRule.waitForIdle()
        expectedPrefill?.let { prefill ->
            composeRule.onNodeWithTag("reps-$exerciseSessionId").assertTextContains(prefill.reps)
            composeRule.onNodeWithTag("load-$exerciseSessionId").assertTextContains(prefill.load)
        } ?: run {
            composeRule.onNodeWithTag("reps-$exerciseSessionId").performTextReplacement(reps.toString())
            composeRule.onNodeWithTag("load-$exerciseSessionId").performTextReplacement(load)
        }

        composeRule.onNodeWithText("Finish").performClick()
        waitForCompletedSessionCount(expectedCompletedSessionCount)
        waitForTextDisplayed(
            text = "Set 1: reps $reps, load ${load}.0 lb",
            substring = true,
        )
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Workout Tracker").assertIsDisplayed()
    }

    private fun waitForDisplayed(testTag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(testTag).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun waitForTextDisplayed(text: String, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(text, substring = substring).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun waitForActiveExerciseSessionId(): Long {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { database.workoutSessionDao().getActiveSessionId() } != null
        }
        return runBlocking {
            val sessionId = database.workoutSessionDao().getActiveSessionId()!!
            sessionRepository.getSessionDetail(sessionId)!!
                .circuits
                .single()
                .exercises
                .single()
                .exerciseSessionId
        }
    }

    private fun waitForCompletedSessionCount(expectedCount: Int) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                database.workoutSessionDao()
                    .getAllSessionDetails()
                    .count { it.session.status == SessionStatus.COMPLETED } == expectedCount &&
                    database.workoutSessionDao().getActiveSessionId() == null
            }
        }
    }

    private fun singleSetWorkout(workoutName: String, exerciseName: String): WorkoutDraft {
        return WorkoutDraft(
            name = workoutName,
            circuits = listOf(
                CircuitDraft(
                    name = "Main",
                    exercises = listOf(
                        ExerciseDraft(
                            name = exerciseName,
                            repMin = 6,
                            repMax = 12,
                            loadMin = 20.0,
                            loadMax = 80.0,
                            setCount = 1,
                        ),
                    ),
                ),
            ),
        )
    }

    private data class PrefillExpectation(
        val reps: String,
        val load: String,
    )

    @Composable
    private fun WorkoutUserFlowTestHarness(container: AppContainer) {
        var route by remember { mutableStateOf<TestRoute>(TestRoute.List) }
        val scope = rememberCoroutineScope()

        when (val currentRoute = route) {
            TestRoute.List -> {
                val viewModel = testViewModel<WorkoutListViewModel>(key = "workout-list") {
                    WorkoutListViewModel(
                        workoutRepository = container.workoutRepository,
                        sessionRepository = container.sessionRepository,
                        importWorkoutsUseCase = container.importWorkoutsUseCase,
                        exportWorkoutDataUseCase = container.exportWorkoutDataUseCase,
                    )
                }
                val state by viewModel.state.collectAsState()
                WorkoutListScreen(
                    state = state,
                    onCreateWorkout = {},
                    onEditWorkout = {},
                    onSelectWorkout = viewModel::selectWorkout,
                    onToggleWorkoutSelection = viewModel::toggleWorkoutSelection,
                    onClearSelection = viewModel::clearSelection,
                    onRequestDeleteSelected = viewModel::requestDeleteSelected,
                    onCancelDelete = viewModel::cancelDelete,
                    onConfirmDelete = viewModel::confirmDelete,
                    onStartWorkout = { workoutId ->
                        scope.launch {
                            route = TestRoute.Session(viewModel.startWorkout(workoutId))
                        }
                    },
                    onResumeWorkout = { sessionId -> route = TestRoute.Session(sessionId) },
                    onOpenHistory = {},
                    onOpenExerciseLibrary = {},
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

            is TestRoute.Session -> {
                val viewModel = testViewModel<ActiveSessionViewModel>(key = "active-session-${currentRoute.sessionId}") {
                    ActiveSessionViewModel(
                        sessionRepository = container.sessionRepository,
                        sessionId = currentRoute.sessionId,
                    )
                }
                val state by viewModel.state.collectAsState()
                LaunchedEffect(state.isCompleted) {
                    if (state.isCompleted) {
                        route = TestRoute.Summary(currentRoute.sessionId)
                    }
                }
                ActiveSessionScreen(
                    state = state,
                    onBack = { route = TestRoute.List },
                    onUpdateReps = viewModel::updateReps,
                    onUpdateLoad = viewModel::updateLoad,
                    onUpdateNotes = viewModel::updateNotes,
                    onUpdateSkipped = viewModel::updateSkipped,
                    onPreviousRound = viewModel::goToPreviousRound,
                    onNextRound = viewModel::goToNextRound,
                    onSelectRound = viewModel::selectRound,
                    onSaveRound = { scope.launch { viewModel.saveRound() } },
                    onAbandonSession = {
                        scope.launch {
                            viewModel.abandonSession()
                            route = TestRoute.List
                        }
                    },
                    onShowExerciseHistory = viewModel::showExerciseHistory,
                    onDismissExerciseHistory = viewModel::dismissExerciseHistory,
                )
            }

            is TestRoute.Summary -> {
                val viewModel = testViewModel<SessionSummaryViewModel>(key = "session-summary-${currentRoute.sessionId}") {
                    SessionSummaryViewModel(
                        sessionRepository = container.sessionRepository,
                        sessionId = currentRoute.sessionId,
                    )
                }
                val state by viewModel.state.collectAsState()
                SessionSummaryScreen(
                    state = state,
                    onBack = { route = TestRoute.List },
                )
            }
        }
    }

    @Composable
    private inline fun <reified T : ViewModel> testViewModel(
        key: String,
        crossinline create: () -> T,
    ): T {
        return viewModel(
            key = key,
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
                    return create() as VM
                }
            },
        )
    }

    private sealed interface TestRoute {
        object List : TestRoute

        data class Session(val sessionId: Long) : TestRoute

        data class Summary(val sessionId: Long) : TestRoute
    }

    private class TestAppContainer(
        database: AppDatabase,
        override val workoutRepository: WorkoutRepository,
        override val sessionRepository: SessionRepository,
    ) : AppContainer {
        override val exerciseDefinitionRepository: ExerciseDefinitionRepository =
            RoomExerciseDefinitionRepository(database.exerciseDefinitionDao())

        override val exportWorkoutDataUseCase: ExportWorkoutDataUseCase =
            ExportWorkoutDataUseCase(
                RoomWorkoutDataExportRepository(
                    exerciseDefinitionDao = database.exerciseDefinitionDao(),
                    workoutTemplateDao = database.workoutTemplateDao(),
                    workoutSessionDao = database.workoutSessionDao(),
                ),
            )

        override val importWorkoutsUseCase: ImportWorkoutsUseCase =
            ImportWorkoutsUseCase(
                workoutRepository = workoutRepository,
                importRepository = RoomWorkoutDataImportRepository(database),
            )

        override val driveBackupSettingsRepository: DriveBackupSettingsRepository = NoOpDriveBackupSettingsRepository()

        private val driveBackupRepository: DriveBackupRepository = NoOpDriveBackupRepository()

        override val setDriveBackupEnabledUseCase: SetDriveBackupEnabledUseCase =
            SetDriveBackupEnabledUseCase(driveBackupSettingsRepository)

        override val backupNowAfterWorkoutCompletionUseCase: BackupNowAfterWorkoutCompletionUseCase =
            BackupNowAfterWorkoutCompletionUseCase(
                exportWorkoutDataUseCase = exportWorkoutDataUseCase,
                driveBackupRepository = driveBackupRepository,
                settingsRepository = driveBackupSettingsRepository,
                accessTokenProvider = object : DriveBackupAccessTokenProvider {
                    override suspend fun getAccessTokenOrNull(): String? = null
                },
                variantKey = "debug",
            )

        override val listDriveBackupSnapshotsUseCase: ListDriveBackupSnapshotsUseCase =
            ListDriveBackupSnapshotsUseCase(
                driveBackupRepository = driveBackupRepository,
                variantKey = "debug",
            )

        override val restoreDriveBackupUseCase: RestoreDriveBackupUseCase =
            RestoreDriveBackupUseCase(
                driveBackupRepository = driveBackupRepository,
                importWorkoutsUseCase = importWorkoutsUseCase,
            )
    }

    private class NoOpDriveBackupSettingsRepository : DriveBackupSettingsRepository {
        override fun observeSettings(): Flow<DriveBackupSettings> = flowOf(DriveBackupSettings())
        override suspend fun getSettings(): DriveBackupSettings = DriveBackupSettings()
        override suspend fun setEnabled(enabled: Boolean) = Unit
        override suspend fun recordBackupSuccess(snapshot: DriveBackupSnapshot) = Unit
        override suspend fun recordBackupFailure(message: String) = Unit
    }

    private class NoOpDriveBackupRepository : DriveBackupRepository {
        override suspend fun uploadSnapshot(
            accessToken: String,
            fileName: String,
            exportedAt: Long,
            json: String,
        ): DriveBackupSnapshot = DriveBackupSnapshot(
            id = "noop",
            fileName = fileName,
            exportedAt = exportedAt,
            modifiedTime = exportedAt,
            sizeBytes = json.length.toLong(),
        )

        override suspend fun listSnapshots(accessToken: String): List<DriveBackupSnapshot> = emptyList()

        override suspend fun downloadSnapshot(accessToken: String, snapshotId: String): String = "{}"

        override suspend fun deleteSnapshot(accessToken: String, snapshotId: String) = Unit
    }
}
