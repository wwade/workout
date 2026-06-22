package dev.wwade.workout.ui.viewmodel

import dev.wwade.workout.domain.exporter.ExportWorkoutDataUseCase
import dev.wwade.workout.domain.exporter.WorkoutDataExportSnapshot
import dev.wwade.workout.domain.importer.ImportWorkoutsUseCase
import dev.wwade.workout.domain.importer.WorkoutImportException
import dev.wwade.workout.domain.importer.WorkoutImportJsonFetcher
import dev.wwade.workout.domain.importer.WorkoutDataRestoreCounts
import dev.wwade.workout.domain.importer.WorkoutDataRestoreSnapshot
import dev.wwade.workout.domain.importer.validWorkoutDataBackupJson
import dev.wwade.workout.domain.importer.validSingleWorkoutJson
import dev.wwade.workout.domain.importer.validSingleWorkoutYaml
import dev.wwade.workout.domain.model.CircuitSessionDetail
import dev.wwade.workout.domain.model.CircuitTemplate
import dev.wwade.workout.domain.model.CompletedSessionListItem
import dev.wwade.workout.domain.model.ExerciseDefinition
import dev.wwade.workout.domain.model.ExerciseSessionDetail
import dev.wwade.workout.domain.model.ExerciseSetHistoryItem
import dev.wwade.workout.domain.model.ExerciseTemplate
import dev.wwade.workout.domain.model.LoadKind
import dev.wwade.workout.domain.model.LoadUnit
import dev.wwade.workout.domain.model.SessionStatus
import dev.wwade.workout.domain.model.SetEntry
import dev.wwade.workout.domain.model.WorkoutDraft
import dev.wwade.workout.domain.model.WorkoutListItem
import dev.wwade.workout.domain.model.WorkoutSessionDetail
import dev.wwade.workout.domain.model.WorkoutTemplate
import dev.wwade.workout.domain.repository.SessionRepository
import dev.wwade.workout.domain.repository.WorkoutDataImportRepository
import dev.wwade.workout.domain.repository.WorkoutDataExportRepository
import dev.wwade.workout.domain.repository.WorkoutRepository
import dev.wwade.workout.ui.state.WorkoutImportDialog
import dev.wwade.workout.util.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun importFromJsonShowsSuccessMessage() = runTest {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModel(repository)
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        viewModel.importFromJson(validSingleWorkoutJson("Imported"))
        advanceUntilIdle()

        assertThat(repository.savedWorkouts.map { it.name }).containsExactly("Imported")
        assertThat(viewModel.state.value.message?.text).contains("Imported 1 workout")
        assertThat(viewModel.state.value.message?.isError).isFalse()
        collector.cancel()
    }

    @Test
    fun importFromJsonShowsPartialSuccessMessage() = runTest {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModel(repository)
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        viewModel.importFromJson(
            """
            {
              "workouts": [
                ${validSingleWorkoutJson("Valid")},
                {"name":"Invalid","circuits":[]}
              ]
            }
            """.trimIndent(),
        )
        advanceUntilIdle()

        assertThat(viewModel.state.value.message?.text).contains("Imported 1 workout; 1 failed")
        assertThat(viewModel.state.value.message?.text).contains("A workout needs at least one circuit")
        assertThat(viewModel.state.value.message?.isError).isFalse()
        collector.cancel()
    }

    @Test
    fun importFromYamlShowsSuccessMessage() = runTest {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModel(repository)
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        viewModel.importFromJson(validSingleWorkoutYaml("Yaml Imported"))
        advanceUntilIdle()

        assertThat(repository.savedWorkouts.map { it.name }).containsExactly("Yaml Imported")
        assertThat(viewModel.state.value.message?.text).contains("Imported 1 workout")
        assertThat(viewModel.state.value.message?.isError).isFalse()
        collector.cancel()
    }

    @Test
    fun fullBackupImportShowsRestoreMessage() = runTest {
        val importRepository = FakeWorkoutDataImportRepository()
        val viewModel = viewModel(
            repository = FakeWorkoutRepository(),
            importRepository = importRepository,
        )
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        viewModel.importFromJson(validWorkoutDataBackupJson())
        advanceUntilIdle()

        assertThat(importRepository.restoredSnapshot?.workouts?.single()?.name).isEqualTo("Push Day")
        assertThat(viewModel.state.value.message?.text)
            .isEqualTo("Restored 1 exercise, 1 workout, and 1 session.")
        assertThat(viewModel.state.value.message?.isError).isFalse()
        collector.cancel()
    }

    @Test
    fun importFromUrlShowsLoadingThenSuccess() = runTest {
        val repository = FakeWorkoutRepository()
        val json = CompletableDeferred<String>()
        val viewModel = viewModel(
            repository = repository,
            fetcher = object : WorkoutImportJsonFetcher {
                override suspend fun fetch(url: String): String = json.await()
            },
        )
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        viewModel.updateImportUrl("https://example.test/workout.json")
        viewModel.importFromUrl()
        runCurrent()

        assertThat(viewModel.state.value.isImporting).isTrue()

        json.complete(validSingleWorkoutJson("Remote"))
        advanceUntilIdle()

        assertThat(viewModel.state.value.isImporting).isFalse()
        assertThat(viewModel.state.value.message?.text).contains("Imported 1 workout")
        collector.cancel()
    }

    @Test
    fun importFailureShowsErrorMessage() = runTest {
        val viewModel = viewModel(
            repository = FakeWorkoutRepository(),
            fetcher = object : WorkoutImportJsonFetcher {
                override suspend fun fetch(url: String): String {
                    throw WorkoutImportException("Unable to download the workout file.")
                }
            },
        )
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        viewModel.updateImportUrl("https://example.test/workout.json")
        viewModel.importFromUrl()
        advanceUntilIdle()

        assertThat(viewModel.state.value.message?.text).isEqualTo("Unable to download the workout file.")
        assertThat(viewModel.state.value.message?.isError).isTrue()
        collector.cancel()
    }

    @Test
    fun prepareExportReturnsJsonAndSuggestedFilename() = runTest {
        val viewModel = viewModel(
            repository = FakeWorkoutRepository(),
            exportRepository = FakeWorkoutDataExportRepository(),
        )
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        val export = viewModel.prepareExport()

        assertThat(export).isNotNull()
        assertThat(export?.fileName).isEqualTo("workout-tracker-export-2026-06-21.json")
        assertThat(export?.content).contains("\"schemaVersion\": 2")
        assertThat(export?.content).contains("\"workoutName\": \"Leg Day\"")
        collector.cancel()
    }

    @Test
    fun exportCallbacksUpdateMessageState() = runTest {
        val viewModel = viewModel(FakeWorkoutRepository())
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        viewModel.onExportSaved("backup.json")
        advanceUntilIdle()
        assertThat(viewModel.state.value.isExporting).isFalse()
        assertThat(viewModel.state.value.message?.text).isEqualTo("Exported workout data to backup.json.")

        viewModel.onExportFailed("disk full")
        advanceUntilIdle()
        assertThat(viewModel.state.value.message?.text).isEqualTo("disk full")
        assertThat(viewModel.state.value.message?.isError).isTrue()
        collector.cancel()
    }

    @Test
    fun importDialogsUpdateState() = runTest {
        val viewModel = viewModel(FakeWorkoutRepository())
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        viewModel.showImportOptions()
        advanceUntilIdle()
        assertThat(viewModel.state.value.importDialog).isEqualTo(WorkoutImportDialog.ChooseSource)

        viewModel.showUrlImport()
        advanceUntilIdle()
        assertThat(viewModel.state.value.importDialog).isEqualTo(WorkoutImportDialog.UrlInput)

        viewModel.hideImportDialog()
        advanceUntilIdle()
        assertThat(viewModel.state.value.importDialog).isEqualTo(WorkoutImportDialog.None)
        collector.cancel()
    }

    @Test
    fun selectToggleAndClearSelectionUpdatesState() = runTest {
        val repository = FakeWorkoutRepository()
        repository.setWorkouts(listOf(workoutItem(1), workoutItem(2)))
        val viewModel = viewModel(repository)
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        viewModel.selectWorkout(1)
        viewModel.toggleWorkoutSelection(2)
        advanceUntilIdle()

        assertThat(viewModel.state.value.selectedWorkoutIds).containsExactly(1L, 2L)
        assertThat(viewModel.state.value.isSelectionMode).isTrue()

        viewModel.toggleWorkoutSelection(1)
        advanceUntilIdle()

        assertThat(viewModel.state.value.selectedWorkoutIds).containsExactly(2L)

        viewModel.clearSelection()
        advanceUntilIdle()

        assertThat(viewModel.state.value.selectedWorkoutIds).isEmpty()
        assertThat(viewModel.state.value.isSelectionMode).isFalse()
        collector.cancel()
    }

    @Test
    fun confirmDeleteCallsRepositoryAndClearsSelection() = runTest {
        val repository = FakeWorkoutRepository()
        repository.setWorkouts(listOf(workoutItem(1), workoutItem(2)))
        val viewModel = viewModel(repository)
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        viewModel.selectWorkout(1)
        viewModel.selectWorkout(2)
        viewModel.requestDeleteSelected()
        advanceUntilIdle()

        assertThat(viewModel.state.value.pendingDeleteCount).isEqualTo(2)

        viewModel.confirmDelete()
        advanceUntilIdle()

        assertThat(repository.deletedWorkoutIds).containsExactly(1L, 2L).inOrder()
        assertThat(viewModel.state.value.selectedWorkoutIds).isEmpty()
        assertThat(viewModel.state.value.pendingDeleteCount).isEqualTo(0)
        collector.cancel()
    }

    @Test
    fun cancelDeleteKeepsSelection() = runTest {
        val repository = FakeWorkoutRepository()
        repository.setWorkouts(listOf(workoutItem(1)))
        val viewModel = viewModel(repository)
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        viewModel.selectWorkout(1)
        viewModel.requestDeleteSelected()
        viewModel.cancelDelete()
        advanceUntilIdle()

        assertThat(viewModel.state.value.selectedWorkoutIds).containsExactly(1L)
        assertThat(viewModel.state.value.pendingDeleteCount).isEqualTo(0)
        collector.cancel()
    }

    @Test
    fun staleSelectedWorkoutsAreRemovedWhenWorkoutListChanges() = runTest {
        val repository = FakeWorkoutRepository()
        repository.setWorkouts(listOf(workoutItem(1), workoutItem(2)))
        val viewModel = viewModel(repository)
        val collector = backgroundScope.launch {
            viewModel.state.collectLatest { }
        }

        viewModel.selectWorkout(1)
        viewModel.selectWorkout(2)
        advanceUntilIdle()

        repository.setWorkouts(listOf(workoutItem(2)))
        advanceUntilIdle()

        assertThat(viewModel.state.value.selectedWorkoutIds).containsExactly(2L)
        collector.cancel()
    }

    private fun viewModel(
        repository: FakeWorkoutRepository,
        fetcher: WorkoutImportJsonFetcher = object : WorkoutImportJsonFetcher {
            override suspend fun fetch(url: String): String = validSingleWorkoutJson("Remote")
        },
        importRepository: WorkoutDataImportRepository = FakeWorkoutDataImportRepository(),
        exportRepository: WorkoutDataExportRepository = FakeWorkoutDataExportRepository(),
    ): WorkoutListViewModel {
        return WorkoutListViewModel(
            workoutRepository = repository,
            sessionRepository = FakeWorkoutListSessionRepository(),
            importWorkoutsUseCase = ImportWorkoutsUseCase(
                workoutRepository = repository,
                importRepository = importRepository,
                jsonFetcher = fetcher,
            ),
            exportWorkoutDataUseCase = ExportWorkoutDataUseCase(
                exportRepository = exportRepository,
                clock = Clock.fixed(Instant.parse("2026-06-21T07:00:00Z"), ZoneOffset.UTC),
            ),
        )
    }
}

private class FakeWorkoutDataImportRepository : WorkoutDataImportRepository {
    var restoredSnapshot: WorkoutDataRestoreSnapshot? = null
        private set

    override suspend fun restoreSnapshot(snapshot: WorkoutDataRestoreSnapshot): WorkoutDataRestoreCounts {
        restoredSnapshot = snapshot
        return WorkoutDataRestoreCounts(
            exerciseDefinitionCount = snapshot.exerciseDefinitions.size,
            workoutCount = snapshot.workouts.size,
            sessionCount = snapshot.sessions.size,
        )
    }
}

private class FakeWorkoutRepository : WorkoutRepository {
    val savedWorkouts = mutableListOf<WorkoutDraft>()
    val deletedWorkoutIds = mutableListOf<Long>()
    private val workouts = MutableStateFlow<List<WorkoutListItem>>(emptyList())

    override fun observeWorkouts(): Flow<List<WorkoutListItem>> = workouts

    fun setWorkouts(items: List<WorkoutListItem>) {
        workouts.value = items
    }

    override suspend fun getWorkout(workoutId: Long): WorkoutTemplate? = null

    override suspend fun saveWorkout(workout: WorkoutDraft): Long {
        savedWorkouts += workout
        return savedWorkouts.size.toLong()
    }

    override suspend fun deleteWorkout(workoutId: Long) {
        deletedWorkoutIds += workoutId
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

private class FakeWorkoutListSessionRepository : SessionRepository {
    override fun observeActiveSessionId(): Flow<Long?> = flowOf(null)

    override suspend fun startWorkout(workoutId: Long): Long = workoutId

    override suspend fun abandonSession(sessionId: Long) = Unit

    override suspend fun saveRoundEntries(
        sessionId: Long,
        entries: List<dev.wwade.workout.domain.model.SetEntryDraft>,
    ) = Unit

    override fun observeSessionDetail(
        sessionId: Long,
    ): Flow<WorkoutSessionDetail?> = flowOf(null)

    override suspend fun getSessionDetail(
        sessionId: Long,
    ): WorkoutSessionDetail? = null

    override fun observeCompletedSessions(): Flow<List<CompletedSessionListItem>> = flowOf(emptyList())

    override suspend fun deleteCompletedSessions(sessionIds: Set<Long>) = Unit

    override suspend fun getLatestCompletedSetEntry(
        exerciseDefinitionId: Long,
        setIndex: Int,
    ): SetEntry? = null

    override suspend fun getPreviousWorkoutSetEntries(
        exerciseDefinitionId: Long,
    ): List<ExerciseSetHistoryItem> = emptyList()

    override suspend fun getRecentCompletedSetEntries(
        exerciseDefinitionId: Long,
        limit: Int,
    ): List<ExerciseSetHistoryItem> = emptyList()
}

private class FakeWorkoutDataExportRepository : WorkoutDataExportRepository {
    override suspend fun exportSnapshot(): WorkoutDataExportSnapshot {
        return WorkoutDataExportSnapshot(
            exerciseDefinitions = listOf(
                ExerciseDefinition(
                    id = 30,
                    name = "Squat",
                    normalizedName = "squat",
                    defaultGuidance = "Stay braced",
                    archived = false,
                    createdAt = 10,
                    updatedAt = 20,
                ),
            ),
            workouts = listOf(
                WorkoutTemplate(
                    id = 1,
                    name = "Leg Day",
                    sortOrder = 0,
                    createdAt = 10,
                    updatedAt = 20,
                    circuits = listOf(
                        CircuitTemplate(
                            id = 2,
                            workoutId = 1,
                            name = "Main",
                            sortOrder = 0,
                            exercises = listOf(
                                ExerciseTemplate(
                                    id = 3,
                                    circuitId = 2,
                                    exerciseDefinitionId = 30,
                                    name = "Squat",
                                    guidance = "Stay braced",
                                    guidanceOverride = "Stay braced",
                                    repMin = 5,
                                    repMax = 8,
                                    loadKind = LoadKind.WEIGHT,
                                    loadMin = 100.0,
                                    loadMax = 160.0,
                                    loadUnit = LoadUnit.LB,
                                    restTimeSeconds = 120,
                                    setCount = 3,
                                    sortOrder = 0,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            sessions = listOf(
                WorkoutSessionDetail(
                    sessionId = 11,
                    workoutTemplateId = 1,
                    workoutName = "Leg Day",
                    startedAt = 100,
                    completedAt = 200,
                    status = SessionStatus.COMPLETED,
                    circuits = listOf(
                        CircuitSessionDetail(
                            circuitSessionId = 12,
                            circuitTemplateId = 2,
                            name = "Main",
                            sortOrder = 0,
                            setCount = 3,
                            exercises = listOf(
                                ExerciseSessionDetail(
                                    exerciseSessionId = 13,
                                    exerciseTemplateId = 3,
                                    exerciseDefinitionId = 30,
                                    name = "Squat",
                                    guidance = "Stay braced",
                                    repMin = 5,
                                    repMax = 8,
                                    loadKind = LoadKind.WEIGHT,
                                    loadMin = 100.0,
                                    loadMax = 160.0,
                                    loadUnit = LoadUnit.LB,
                                    restTimeSeconds = 120,
                                    sortOrder = 0,
                                    sets = listOf(
                                        SetEntry(
                                            id = 14,
                                            exerciseSessionId = 13,
                                            setIndex = 0,
                                            repsActual = 5,
                                            loadActual = 135.0,
                                            notes = "Solid",
                                            skipped = false,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
