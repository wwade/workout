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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.wwade.workout.domain.model.ExerciseDefinition
import dev.wwade.workout.domain.model.ExerciseDefinitionDraft
import dev.wwade.workout.ui.state.ExerciseLibraryState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    state: ExerciseLibraryState,
    onBack: () -> Unit,
    onUpdateSearch: (String) -> Unit,
    onSaveExercise: suspend (ExerciseDefinitionDraft) -> Boolean,
    onArchiveExercise: suspend (Long, Boolean) -> Unit,
    onDismissError: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var editingExercise by remember { mutableStateOf<ExerciseDefinition?>(null) }
    var addingExercise by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exercises") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addingExercise = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add exercise")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onUpdateSearch,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search exercises") },
                singleLine = true,
            )
            state.saveError?.let { error ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onDismissError) { Text("Dismiss") }
                    }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.filteredExercises, key = { it.id }) { exercise ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                                if (exercise.defaultGuidance.isNotBlank()) {
                                    Text(exercise.defaultGuidance)
                                }
                                Text(
                                    text = "${exercise.usageCount} workout uses${if (exercise.archived) " | archived" else ""}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Row {
                                IconButton(onClick = { editingExercise = exercise }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            onArchiveExercise(exercise.id, !exercise.archived)
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = if (exercise.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                                        contentDescription = if (exercise.archived) "Unarchive" else "Archive",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (addingExercise || editingExercise != null) {
        val initial = editingExercise
        ExerciseDefinitionDialog(
            title = if (initial == null) "Add Exercise" else "Edit Exercise",
            initial = initial,
            onDismiss = {
                addingExercise = false
                editingExercise = null
            },
            onSave = { draft ->
                scope.launch {
                    if (onSaveExercise(draft)) {
                        addingExercise = false
                        editingExercise = null
                    }
                }
            },
        )
    }
}

@Composable
private fun ExerciseDefinitionDialog(
    title: String,
    initial: ExerciseDefinition?,
    onDismiss: () -> Unit,
    onSave: (ExerciseDefinitionDraft) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var guidance by remember(initial) { mutableStateOf(initial?.defaultGuidance.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = guidance,
                    onValueChange = { guidance = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Default guidance") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ExerciseDefinitionDraft(
                            id = initial?.id,
                            name = name,
                            defaultGuidance = guidance,
                            archived = initial?.archived ?: false,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
