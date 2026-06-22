package dev.wwade.workout.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.wwade.workout.domain.model.WorkoutListItem
import dev.wwade.workout.ui.state.WorkoutImportDialog
import dev.wwade.workout.ui.state.WorkoutListState
import dev.wwade.workout.ui.viewmodel.WorkoutExportFile
import kotlinx.coroutines.launch

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
    onOpenExerciseLibrary: () -> Unit,
    onPrepareExport: suspend () -> WorkoutExportFile?,
    onExportSaved: (String) -> Unit,
    onExportFailed: (String) -> Unit,
    onExportCancelled: () -> Unit,
    onShowImportOptions: () -> Unit,
    onHideImportDialog: () -> Unit,
    onShowUrlImport: () -> Unit,
    onUpdateImportUrl: (String) -> Unit,
    onImportFromUrl: () -> Unit,
    onImportFromJson: (String) -> Unit,
    onImportFileReadFailed: (String) -> Unit,
    onDismissImportMessage: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<WorkoutExportFile?>(null) }
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        val payload = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()

        if (payload.isNullOrBlank()) {
            onImportFileReadFailed("Unable to read the selected workout file.")
        } else {
            onImportFromJson(payload)
        }
    }
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val export = pendingExport
        pendingExport = null

        if (export == null || uri == null) {
            onExportCancelled()
            return@rememberLauncherForActivityResult
        }

        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(export.content)
            } ?: error("Unable to open the selected file.")
        }.onSuccess {
            onExportSaved(export.fileName)
        }.onFailure {
            onExportFailed(it.message ?: "Unable to save the export file.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Tracker") },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val export = onPrepareExport() ?: return@launch
                                pendingExport = export
                                exportFileLauncher.launch(export.fileName)
                            }
                        },
                        enabled = !state.isImporting && !state.isExporting,
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export data")
                    }
                    IconButton(
                        onClick = onShowImportOptions,
                        enabled = !state.isImporting && !state.isExporting,
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import workouts")
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = onOpenExerciseLibrary) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = "Exercises")
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
        WorkoutListContent(
            state = state,
            padding = padding,
            onEditWorkout = onEditWorkout,
            onDeleteWorkout = onDeleteWorkout,
            onStartWorkout = onStartWorkout,
            onResumeWorkout = onResumeWorkout,
            onDismissImportMessage = onDismissImportMessage,
        )
    }

    when (state.importDialog) {
        WorkoutImportDialog.None -> Unit
        WorkoutImportDialog.ChooseSource -> {
            AlertDialog(
                onDismissRequest = onHideImportDialog,
                title = { Text("Import workouts") },
                text = { Text("Choose a JSON or YAML source to import workout templates.") },
                confirmButton = {
                    Button(
                        onClick = {
                            onHideImportDialog()
                            importFileLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "application/yaml",
                                    "application/x-yaml",
                                    "text/yaml",
                                    "text/x-yaml",
                                    "text/*",
                                ),
                            )
                        },
                    ) {
                        Text("Local file")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onShowUrlImport) {
                        Text("URL")
                    }
                },
            )
        }

        WorkoutImportDialog.UrlInput -> {
            AlertDialog(
                onDismissRequest = onHideImportDialog,
                title = { Text("Import from URL") },
                text = {
                    OutlinedTextField(
                        value = state.importUrl,
                        onValueChange = onUpdateImportUrl,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Direct JSON or YAML URL") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onImportFromUrl,
                        enabled = !state.isImporting,
                    ) {
                        Text("Import")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onHideImportDialog) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun WorkoutListContent(
    state: WorkoutListState,
    padding: PaddingValues,
    onEditWorkout: (Long) -> Unit,
    onDeleteWorkout: (WorkoutListItem) -> Unit,
    onStartWorkout: (Long) -> Unit,
    onResumeWorkout: (Long) -> Unit,
    onDismissImportMessage: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        if (state.isImporting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (state.isExporting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.message?.let { message ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = message.text,
                        color = if (message.isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    TextButton(onClick = onDismissImportMessage) {
                        Text("Dismiss")
                    }
                }
            }
        }

        if (state.workouts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                    .fillMaxSize(),
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
