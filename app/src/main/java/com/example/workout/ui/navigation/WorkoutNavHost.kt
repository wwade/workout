package com.example.workout.ui.navigation

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
import com.example.workout.AppContainer
import com.example.workout.ui.screen.ActiveSessionScreen
import com.example.workout.ui.screen.HistoryScreen
import com.example.workout.ui.screen.SessionSummaryScreen
import com.example.workout.ui.screen.WorkoutEditorScreen
import com.example.workout.ui.screen.WorkoutListScreen
import com.example.workout.ui.viewmodel.ActiveSessionViewModel
import com.example.workout.ui.viewmodel.AppViewModelFactory
import com.example.workout.ui.viewmodel.HistoryViewModel
import com.example.workout.ui.viewmodel.SessionSummaryViewModel
import com.example.workout.ui.viewmodel.WorkoutEditorViewModel
import com.example.workout.ui.viewmodel.WorkoutListViewModel
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
