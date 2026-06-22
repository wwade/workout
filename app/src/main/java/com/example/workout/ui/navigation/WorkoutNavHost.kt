package dev.wwade.workout.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.wwade.workout.AppContainer
import dev.wwade.workout.ui.screen.ActiveSessionScreen
import dev.wwade.workout.ui.screen.ExerciseLibraryScreen
import dev.wwade.workout.ui.screen.HistoryScreen
import dev.wwade.workout.ui.screen.SessionSummaryScreen
import dev.wwade.workout.ui.screen.WorkoutEditorScreen
import dev.wwade.workout.ui.screen.WorkoutListScreen
import dev.wwade.workout.ui.viewmodel.ActiveSessionViewModel
import dev.wwade.workout.ui.viewmodel.AppViewModelFactory
import dev.wwade.workout.ui.viewmodel.ExerciseLibraryViewModel
import dev.wwade.workout.ui.viewmodel.HistoryViewModel
import dev.wwade.workout.ui.viewmodel.SessionSummaryViewModel
import dev.wwade.workout.ui.viewmodel.WorkoutEditorViewModel
import dev.wwade.workout.ui.viewmodel.WorkoutListViewModel
import kotlinx.coroutines.launch

@Composable
fun WorkoutNavHost(
    container: AppContainer,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = "workouts",
    ) {
        composable("workouts") {
            val viewModel: WorkoutListViewModel = viewModel(
                factory = AppViewModelFactory.create {
                    WorkoutListViewModel(
                        workoutRepository = container.workoutRepository,
                        sessionRepository = container.sessionRepository,
                        importWorkoutsUseCase = container.importWorkoutsUseCase,
                        exportWorkoutDataUseCase = container.exportWorkoutDataUseCase,
                    )
                },
            )
            val state by viewModel.state.collectAsState()
            WorkoutListScreen(
                state = state,
                onCreateWorkout = { navController.navigate("editor") },
                onEditWorkout = { workoutId -> navController.navigate("editor?workoutId=$workoutId") },
                onDeleteWorkout = viewModel::deleteWorkout,
                onStartWorkout = { workoutId ->
                    scope.launch {
                        val sessionId = viewModel.startWorkout(workoutId)
                        navController.navigate("session/$sessionId")
                    }
                },
                onResumeWorkout = { sessionId -> navController.navigate("session/$sessionId") },
                onOpenHistory = { navController.navigate("history") },
                onOpenExerciseLibrary = { navController.navigate("exercises") },
                onPrepareExport = viewModel::prepareExport,
                onExportSaved = viewModel::onExportSaved,
                onExportFailed = viewModel::onExportFailed,
                onExportCancelled = viewModel::onExportCancelled,
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

        composable(
            route = "editor?workoutId={workoutId}",
            arguments = listOf(
                navArgument("workoutId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getLong("workoutId")?.takeIf { it >= 0 }
            val viewModel: WorkoutEditorViewModel = viewModel(
                key = "editor-$workoutId",
                factory = AppViewModelFactory.create {
                    WorkoutEditorViewModel(
                        workoutRepository = container.workoutRepository,
                        exerciseDefinitionRepository = container.exerciseDefinitionRepository,
                        workoutId = workoutId,
                    )
                },
            )
            val state by viewModel.state.collectAsState()
            WorkoutEditorScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onUpdateWorkoutName = viewModel::updateWorkoutName,
                onUpsertCircuit = viewModel::upsertCircuit,
                onRemoveCircuit = viewModel::removeCircuit,
                onMoveCircuit = viewModel::moveCircuit,
                onUpsertExercise = viewModel::upsertExercise,
                onRemoveExercise = viewModel::removeExercise,
                onMoveExercise = viewModel::moveExercise,
                onSave = {
                    scope.launch {
                        val savedId = viewModel.saveWorkout()
                        if (savedId != null) {
                            navController.popBackStack()
                        }
                    }
                },
            )
        }

        composable(
            route = "session/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0
            val viewModel: ActiveSessionViewModel = viewModel(
                key = "session-$sessionId",
                factory = AppViewModelFactory.create {
                    ActiveSessionViewModel(
                        sessionRepository = container.sessionRepository,
                        sessionId = sessionId,
                    )
                },
            )
            val state by viewModel.state.collectAsState()
            LaunchedEffect(state.isCompleted) {
                if (state.isCompleted) {
                    navController.navigate("summary/$sessionId") {
                        popUpTo("workouts")
                    }
                }
            }
            ActiveSessionScreen(
                state = state,
                onBack = { navController.popBackStack() },
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
                        navController.navigate("workouts") {
                            popUpTo("workouts") { inclusive = true }
                        }
                    }
                },
                onShowExerciseHistory = viewModel::showExerciseHistory,
                onDismissExerciseHistory = viewModel::dismissExerciseHistory,
            )
        }

        composable("history") {
            val viewModel: HistoryViewModel = viewModel(
                factory = AppViewModelFactory.create {
                    HistoryViewModel(sessionRepository = container.sessionRepository)
                },
            )
            val state by viewModel.state.collectAsState()
            HistoryScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onOpenSession = { sessionId -> navController.navigate("summary/$sessionId") },
                onSelectSession = viewModel::selectSession,
                onToggleSessionSelection = viewModel::toggleSessionSelection,
                onClearSelection = viewModel::clearSelection,
                onRequestDeleteSelected = viewModel::requestDeleteSelected,
                onCancelDelete = viewModel::cancelDelete,
                onConfirmDelete = viewModel::confirmDelete,
            )
        }

        composable("exercises") {
            val viewModel: ExerciseLibraryViewModel = viewModel(
                factory = AppViewModelFactory.create {
                    ExerciseLibraryViewModel(
                        exerciseDefinitionRepository = container.exerciseDefinitionRepository,
                    )
                },
            )
            val state by viewModel.state.collectAsState()
            ExerciseLibraryScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onUpdateSearch = viewModel::updateSearchQuery,
                onSaveExercise = viewModel::saveExercise,
                onArchiveExercise = viewModel::archiveExercise,
                onDismissError = viewModel::clearError,
            )
        }

        composable(
            route = "summary/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0
            val viewModel: SessionSummaryViewModel = viewModel(
                key = "summary-$sessionId",
                factory = AppViewModelFactory.create {
                    SessionSummaryViewModel(
                        sessionRepository = container.sessionRepository,
                        sessionId = sessionId,
                    )
                },
            )
            val state by viewModel.state.collectAsState()
            SessionSummaryScreen(
                state = state,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
