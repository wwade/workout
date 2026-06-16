package dev.wwade.workout.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.wwade.workout.domain.model.WorkoutListItem
import dev.wwade.workout.ui.state.WorkoutListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutListScreen(
    state: WorkoutListState,
    onCreateWorkout: () -> Unit,
    onEditWorkout: (Long) -> Unit,
    onDeleteWorkout: (WorkoutListItem) -> Unit,
    onStartWorkout: (Long) -> Unit,
    onResumeWorkout: (Long) -> Unit,
    onOpenHistory: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Tracker") },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateWorkout) {
                Icon(Icons.Default.Add, contentDescription = "Create workout")
            }
        },
    ) { padding ->
        if (state.workouts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No workouts yet",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Create your first workout template with circuits and exercises.",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.workouts, key = { it.id }) { workout ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = workout.name,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "${workout.circuitCount} circuits | ${workout.exerciseCount} exercises",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row {
                                    IconButton(onClick = { onEditWorkout(workout.id) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = { onDeleteWorkout(workout) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                                if (state.activeSessionId != null) {
                                    TextButton(onClick = { onResumeWorkout(state.activeSessionId) }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Text("Resume")
                                    }
                                } else {
                                    TextButton(onClick = { onStartWorkout(workout.id) }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Text("Start")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
