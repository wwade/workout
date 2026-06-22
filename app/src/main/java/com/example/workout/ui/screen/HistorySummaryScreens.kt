package dev.wwade.workout.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
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
    onSelectSession: (Long) -> Unit,
    onToggleSessionSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    BackHandler(enabled = state.isSelectionMode, onBack = onClearSelection)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isSelectionMode) {
                            "${state.selectedSessionIds.size} selected"
                        } else {
                            "History"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.isSelectionMode) onClearSelection() else onBack()
                        },
                    ) {
                        Icon(
                            imageVector = if (state.isSelectionMode) {
                                Icons.Default.Close
                            } else {
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft
                            },
                            contentDescription = if (state.isSelectionMode) "Clear selection" else "Back",
                        )
                    }
                },
                actions = {
                    if (state.isSelectionMode) {
                        IconButton(onClick = onRequestDeleteSelected) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected history")
                        }
                    }
                },
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
                HistorySessionRow(
                    sessionId = session.sessionId,
                    workoutName = session.workoutName,
                    completedAt = session.completedAt,
                    circuitCount = session.circuitCount,
                    selected = session.sessionId in state.selectedSessionIds,
                    selectionMode = state.isSelectionMode,
                    onClick = {
                        if (state.isSelectionMode) {
                            onToggleSessionSelection(session.sessionId)
                        } else {
                            onOpenSession(session.sessionId)
                        }
                    },
                    onLongClick = { onSelectSession(session.sessionId) },
                )
            }
        }
    }

    if (state.pendingDeleteCount > 0) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("Delete history?") },
            text = {
                Text(
                    "Delete ${state.pendingDeleteCount} completed workout " +
                        if (state.pendingDeleteCount == 1) "session?" else "sessions?",
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistorySessionRow(
    sessionId: Long,
    workoutName: String,
    completedAt: Long,
    circuitCount: Int,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history-row-$sessionId")
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        ListItem(
            headlineContent = {
                Text(workoutName, style = MaterialTheme.typography.titleMedium)
            },
            supportingContent = {
                Column {
                    Text("Completed ${formatDateTime(completedAt)}")
                    Text("$circuitCount circuits")
                }
            },
            leadingContent = {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        modifier = Modifier.testTag("history-checkbox-$sessionId"),
                        onCheckedChange = { onClick() },
                    )
                }
            },
        )
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
