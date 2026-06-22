package dev.wwade.workout.ui.screen

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import dev.wwade.workout.domain.backup.DRIVE_APPDATA_SCOPE
import dev.wwade.workout.domain.backup.DriveBackupSnapshot
import dev.wwade.workout.domain.model.WorkoutListItem
import dev.wwade.workout.ui.state.DriveBackupAuthorizationAction
import dev.wwade.workout.ui.state.DriveBackupDialog
import dev.wwade.workout.ui.state.WorkoutImportDialog
import dev.wwade.workout.ui.state.WorkoutListState
import dev.wwade.workout.ui.viewmodel.WorkoutExportFile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutListScreen(
    state: WorkoutListState,
    onCreateWorkout: () -> Unit,
    onEditWorkout: (Long) -> Unit,
    onSelectWorkout: (Long) -> Unit,
    onToggleWorkoutSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
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
    onShowDriveBackupOptions: () -> Unit = {},
    onHideDriveBackupDialog: () -> Unit = {},
    onDisableDriveBackup: () -> Unit = {},
    onDriveAuthorizationToken: (DriveBackupAuthorizationAction, String) -> Unit = { _, _ -> },
    onDriveAuthorizationFailed: (String) -> Unit = {},
    onRequestRestoreDriveBackup: (String) -> Unit = {},
    onCancelRestoreDriveBackup: () -> Unit = {},
    onConfirmRestoreDriveBackup: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<WorkoutExportFile?>(null) }
    var pendingActiveWorkoutEditId by remember { mutableStateOf<Long?>(null) }
    var pendingDriveAction by remember { mutableStateOf<DriveBackupAuthorizationAction?>(null) }

    BackHandler(enabled = state.isSelectionMode, onBack = onClearSelection)

    val startDriveAuthorizationIntent = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val action = pendingDriveAction
        pendingDriveAction = null
        if (action == null) return@rememberLauncherForActivityResult

        runCatching {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data)
        }.onSuccess { authorizationResult ->
            val accessToken = authorizationResult.accessToken
            if (accessToken.isNullOrBlank()) {
                onDriveAuthorizationFailed("Google Drive permission did not return an access token.")
            } else {
                onDriveAuthorizationToken(action, accessToken)
            }
        }.onFailure { error ->
            val message = if (error is ApiException) {
                error.message ?: "Google Drive permission was not granted."
            } else {
                "Google Drive permission was not granted."
            }
            onDriveAuthorizationFailed(message)
        }
    }

    fun requestDriveAuthorization(action: DriveBackupAuthorizationAction) {
        val activity = context as? Activity
        if (activity == null) {
            onDriveAuthorizationFailed("Drive backup needs an Android activity.")
            return
        }
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        onDriveAuthorizationFailed("Unable to open Google Drive permission.")
                    } else {
                        pendingDriveAction = action
                        startDriveAuthorizationIntent.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                        )
                    }
                } else {
                    val accessToken = result.accessToken
                    if (accessToken.isNullOrBlank()) {
                        onDriveAuthorizationFailed("Google Drive permission did not return an access token.")
                    } else {
                        onDriveAuthorizationToken(action, accessToken)
                    }
                }
            }
            .addOnFailureListener { error ->
                onDriveAuthorizationFailed(error.message ?: "Unable to request Google Drive permission.")
            }
    }

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
                title = {
                    Text(
                        if (state.isSelectionMode) {
                            "${state.selectedWorkoutIds.size} selected"
                        } else {
                            "Workout Tracker"
                        },
                    )
                },
                navigationIcon = {
                    if (state.isSelectionMode) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    }
                },
                actions = {
                    if (state.isSelectionMode) {
                        IconButton(onClick = onRequestDeleteSelected) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected workouts")
                        }
                    } else {
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
                            Icon(Icons.Default.FileUpload, contentDescription = "Import data")
                        }
                        IconButton(
                            onClick = onShowDriveBackupOptions,
                            enabled = !state.isImporting && !state.isExporting && !state.isDriveBackupBusy,
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = "Drive backup")
                        }
                        IconButton(onClick = onOpenHistory) {
                            Icon(Icons.Default.History, contentDescription = "History")
                        }
                        IconButton(onClick = onOpenExerciseLibrary) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = "Exercises")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isSelectionMode) {
                FloatingActionButton(onClick = onCreateWorkout) {
                    Icon(Icons.Default.Add, contentDescription = "Create workout")
                }
            }
        },
    ) { padding ->
        WorkoutListContent(
            state = state,
            padding = padding,
            onEditWorkout = { workoutId ->
                if (workoutId == state.activeSession?.workoutTemplateId) {
                    pendingActiveWorkoutEditId = workoutId
                } else {
                    onEditWorkout(workoutId)
                }
            },
            onSelectWorkout = onSelectWorkout,
            onToggleWorkoutSelection = onToggleWorkoutSelection,
            onStartWorkout = onStartWorkout,
            onResumeWorkout = onResumeWorkout,
            onDismissImportMessage = onDismissImportMessage,
        )
    }

    if (state.pendingDeleteCount > 0) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("Delete workouts?") },
            text = {
                Text(
                    "Delete ${state.pendingDeleteCount} workout " +
                        if (state.pendingDeleteCount == 1) "template?" else "templates?",
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

    pendingActiveWorkoutEditId?.let { workoutId ->
        AlertDialog(
            onDismissRequest = { pendingActiveWorkoutEditId = null },
            title = { Text("Edit workout?") },
            text = {
                Text(
                    "Editing this workout will not affect the workout currently in progress. " +
                        "Changes will apply the next time you start it.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingActiveWorkoutEditId = null
                        onEditWorkout(workoutId)
                    },
                ) {
                    Text("Edit")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingActiveWorkoutEditId = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    when (state.importDialog) {
        WorkoutImportDialog.None -> Unit
        WorkoutImportDialog.ChooseSource -> {
            AlertDialog(
                onDismissRequest = onHideImportDialog,
                title = { Text("Import data") },
                text = { Text("Choose a JSON or YAML workout-template source, or a JSON backup exported from this app.") },
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

    when (state.driveBackupDialog) {
        DriveBackupDialog.None -> Unit
        DriveBackupDialog.Options -> {
            AlertDialog(
                onDismissRequest = onHideDriveBackupDialog,
                title = { Text("Drive backup") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (state.driveBackupSettings.enabled) {
                                "Automatic Drive backup is enabled."
                            } else {
                                "Automatic Drive backup is disabled."
                            },
                        )
                        state.driveBackupSettings.lastSuccessAt?.let { lastSuccessAt ->
                            Text("Last backup: ${formatDriveBackupTime(lastSuccessAt)}")
                        }
                        state.driveBackupSettings.lastFailureMessage?.let { failure ->
                            Text(
                                text = failure,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    if (state.driveBackupSettings.enabled) {
                        TextButton(onClick = onDisableDriveBackup) {
                            Text("Disable")
                        }
                    } else {
                        Button(
                            onClick = {
                                requestDriveAuthorization(DriveBackupAuthorizationAction.Enable)
                            },
                            enabled = !state.isDriveBackupBusy,
                        ) {
                            Text("Enable")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            requestDriveAuthorization(DriveBackupAuthorizationAction.ListSnapshots)
                        },
                        enabled = !state.isDriveBackupBusy,
                    ) {
                        Text("Restore")
                    }
                },
            )
        }

        DriveBackupDialog.Snapshots -> {
            AlertDialog(
                onDismissRequest = onHideDriveBackupDialog,
                title = { Text("Restore from Drive") },
                text = {
                    if (state.driveBackupSnapshots.isEmpty()) {
                        Text("No Drive backups were found.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.driveBackupSnapshots, key = { it.id }) { snapshot ->
                                TextButton(
                                    onClick = { onRequestRestoreDriveBackup(snapshot.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(formatDriveBackupTime(snapshot.exportedAt))
                                        Text(
                                            text = snapshot.sizeBytes?.let(::formatDriveBackupSize)
                                                ?: snapshot.fileName,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onHideDriveBackupDialog) {
                        Text("Close")
                    }
                },
            )
        }

        DriveBackupDialog.ConfirmRestore -> {
            val snapshot = state.pendingRestoreSnapshot
            AlertDialog(
                onDismissRequest = onCancelRestoreDriveBackup,
                title = { Text("Restore backup?") },
                text = {
                    Text(
                        "This will replace current workout data with " +
                            "${snapshot?.let { formatDriveBackupTime(it.exportedAt) } ?: "the selected backup"}.",
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onConfirmRestoreDriveBackup,
                        enabled = !state.isDriveBackupBusy,
                    ) {
                        Text("Restore")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onCancelRestoreDriveBackup) {
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
    onSelectWorkout: (Long) -> Unit,
    onToggleWorkoutSelection: (Long) -> Unit,
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

        if (state.isDriveBackupBusy) {
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

        state.activeSession?.let { activeSession ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("active-session-banner"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Workout in progress: ${activeSession.workoutName}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        onClick = { onResumeWorkout(activeSession.sessionId) },
                        modifier = Modifier.testTag("resume-active-session"),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("Resume")
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
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.workouts, key = { it.id }) { workout ->
                    WorkoutRow(
                        workout = workout,
                        selected = workout.id in state.selectedWorkoutIds,
                        selectionMode = state.isSelectionMode,
                        hasActiveSession = state.activeSession != null,
                        onClick = {
                            if (state.isSelectionMode) {
                                onToggleWorkoutSelection(workout.id)
                            }
                        },
                        onLongClick = { onSelectWorkout(workout.id) },
                        onEditWorkout = onEditWorkout,
                        onStartWorkout = onStartWorkout,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutRow(
    workout: WorkoutListItem,
    selected: Boolean,
    selectionMode: Boolean,
    hasActiveSession: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEditWorkout: (Long) -> Unit,
    onStartWorkout: (Long) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("workout-row-${workout.id}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    modifier = Modifier.testTag("workout-checkbox-${workout.id}"),
                    onCheckedChange = { onClick() },
                )
            }
            Column(
                modifier = Modifier.weight(1f),
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
                if (!selectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { onEditWorkout(workout.id) },
                            modifier = Modifier.testTag("edit-workout-${workout.id}"),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        if (!hasActiveSession) {
                            TextButton(
                                onClick = { onStartWorkout(workout.id) },
                                modifier = Modifier.testTag("start-workout-${workout.id}"),
                            ) {
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

private fun formatDriveBackupTime(epochMillis: Long): String {
    return DriveBackupDateFormatter.format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
    )
}

private fun formatDriveBackupSize(sizeBytes: Long): String {
    return when {
        sizeBytes >= 1_000_000 -> "${sizeBytes / 1_000_000} MB"
        sizeBytes >= 1_000 -> "${sizeBytes / 1_000} KB"
        else -> "$sizeBytes bytes"
    }
}

private val DriveBackupDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
