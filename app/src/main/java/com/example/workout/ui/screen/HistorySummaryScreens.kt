package dev.wwade.workout.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.wwade.workout.ui.state.HistoryState
import dev.wwade.workout.ui.state.SessionSummaryState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryState,
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.sessions, key = { it.sessionId }) { session ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenSession(session.sessionId) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(session.workoutName, style = MaterialTheme.typography.titleMedium)
                        Text("Completed ${formatDateTime(session.completedAt)}")
                        Text("${session.circuitCount} circuits")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSummaryScreen(
    state: SessionSummaryState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.workoutName.ifBlank { "Session summary" }) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Started ${formatDateTime(state.startedAt)}")
                    state.completedAt?.let { Text("Completed ${formatDateTime(it)}") }
                }
            }
            items(state.circuits) { circuit ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(circuit.name, style = MaterialTheme.typography.titleMedium)
                        Text("${circuit.setCount} rounds")
                        circuit.exercises.forEach { exercise ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                                if (exercise.guidance.isNotBlank()) {
                                    Text(exercise.guidance)
                                }
                                Text("Rest ${exercise.restTimeSeconds} sec")
                                exercise.sets.forEach { set ->
                                    Text(
                                        "Set ${set.setNumber}: reps ${set.reps ?: "-"}, load ${set.load ?: "-"} ${exercise.loadUnit.name.lowercase()}${set.notes.takeIf { it.isNotBlank() }?.let { " | $it" }.orEmpty()}${if (set.skipped) " | skipped" else ""}",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDateTime(value: Long): String {
    return DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(value))
}
