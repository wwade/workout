package dev.wwade.workout.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import dev.wwade.workout.domain.model.CircuitDraft
import dev.wwade.workout.domain.model.ExerciseDraft
import dev.wwade.workout.domain.model.LoadKind
import dev.wwade.workout.domain.model.LoadUnit
import dev.wwade.workout.ui.state.WorkoutEditorState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditorScreen(
    state: WorkoutEditorState,
    onBack: () -> Unit,
    onUpdateWorkoutName: (String) -> Unit,
    onUpsertCircuit: (Int?, String) -> Unit,
    onRemoveCircuit: (Int) -> Unit,
    onMoveCircuit: (Int, Int) -> Unit,
    onUpsertExercise: (Int, Int?, ExerciseDraft) -> Unit,
    onRemoveExercise: (Int, Int) -> Unit,
    onMoveExercise: (Int, Int, Int) -> Unit,
    onSave: () -> Unit,
) {
    var editingCircuitIndex by remember { mutableIntStateOf(-1) }
    var addingCircuit by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<Pair<Int, Int?>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.draft.id == null) "Create Workout" else "Edit Workout") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = onSave) { Text("Save") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addingCircuit = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add circuit")
            }
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
                OutlinedTextField(
                    value = state.draft.name,
                    onValueChange = onUpdateWorkoutName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Workout name") },
                    isError = state.validation.workoutNameError != null,
                    supportingText = {
                        state.validation.workoutNameError?.let { Text(it) }
                    },
                )
            }

            state.validation.workoutError?.let { error ->
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            itemsIndexed(state.draft.circuits) { circuitIndex, circuit ->
                val validation = state.validation.circuitValidations.getOrNull(circuitIndex)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(circuit.name.ifBlank { "Unnamed circuit" }, style = MaterialTheme.typography.titleMedium)
                                val rounds = circuit.exercises.firstOrNull()?.setCount ?: 0
                                Text("Rounds: $rounds")
                                validation?.nameError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error)
                                }
                                validation?.circuitError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error)
                                }
                                if (validation?.setCountMismatch == true) {
                                    Text(
                                        text = "All exercises in this circuit must use the same set count.",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            Row {
                                IconButton(onClick = { onMoveCircuit(circuitIndex, -1) }) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                                }
                                IconButton(onClick = { onMoveCircuit(circuitIndex, 1) }) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                                }
                                IconButton(onClick = {
                                    editingCircuitIndex = circuitIndex
                                    addingCircuit = false
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit circuit")
                                }
                                IconButton(onClick = { onRemoveCircuit(circuitIndex) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete circuit")
                                }
                            }
                        }

                        circuit.exercises.forEachIndexed { exerciseIndex, exercise ->
                            val exerciseValidation = validation?.exerciseValidations?.getOrNull(exerciseIndex)
                            ExerciseRow(
                                circuitIndex = circuitIndex,
                                exerciseIndex = exerciseIndex,
                                exercise = exercise,
                                validation = exerciseValidation,
                                onEdit = { editingExercise = circuitIndex to exerciseIndex },
                                onDelete = { onRemoveExercise(circuitIndex, exerciseIndex) },
                                onMoveUp = { onMoveExercise(circuitIndex, exerciseIndex, -1) },
                                onMoveDown = { onMoveExercise(circuitIndex, exerciseIndex, 1) },
                            )
                        }

                        TextButton(onClick = { editingExercise = circuitIndex to null }) {
                            Text("Add exercise")
                        }
                    }
                }
            }

            state.saveError?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (addingCircuit) {
        CircuitDialog(
            initialValue = "",
            title = "Add Circuit",
            onDismiss = { addingCircuit = false },
            onSave = {
                onUpsertCircuit(null, it)
                addingCircuit = false
            },
        )
    }

    if (editingCircuitIndex >= 0) {
        val circuit = state.draft.circuits.getOrNull(editingCircuitIndex)
        if (circuit != null) {
            CircuitDialog(
                initialValue = circuit.name,
                title = "Edit Circuit",
                onDismiss = { editingCircuitIndex = -1 },
                onSave = {
                    onUpsertCircuit(editingCircuitIndex, it)
                    editingCircuitIndex = -1
                },
            )
        }
    }

    editingExercise?.let { (circuitIndex, exerciseIndex) ->
        val initialExercise = exerciseIndex?.let { state.draft.circuits[circuitIndex].exercises[it] } ?: ExerciseDraft()
        ExerciseDialog(
            title = if (exerciseIndex == null) "Add Exercise" else "Edit Exercise",
            initialValue = initialExercise,
            onDismiss = { editingExercise = null },
            onSave = {
                onUpsertExercise(circuitIndex, exerciseIndex, it)
                editingExercise = null
            },
        )
    }
}

@Composable
private fun ExerciseRow(
    circuitIndex: Int,
    exerciseIndex: Int,
    exercise: ExerciseDraft,
    validation: dev.wwade.workout.domain.usecase.ExerciseValidation?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(exercise.name.ifBlank { "Unnamed exercise" }, style = MaterialTheme.typography.titleSmall)
                    if (exercise.guidance.isNotBlank()) {
                        Text(exercise.guidance)
                    }
                    Text("Reps ${exercise.repMin}-${exercise.repMax}")
                    Text(
                        buildString {
                            append(if (exercise.loadKind == LoadKind.WEIGHT) "Weight" else "Time")
                            append(" ${exercise.loadMin}-${exercise.loadMax} ${exercise.loadUnit.name.lowercase()}")
                            append(" | ${exercise.setCount} sets")
                            append(" | Rest ${exercise.restTimeSeconds} sec")
                        },
                    )
                    validation?.nameError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    validation?.setCountError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    validation?.repRangeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    validation?.loadRangeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    validation?.loadUnitError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    validation?.restTimeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                Row {
                    IconButton(onClick = onMoveUp) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                    }
                    IconButton(onClick = onMoveDown) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun CircuitDialog(
    initialValue: String,
    title: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Circuit name") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ExerciseDialog(
    title: String,
    initialValue: ExerciseDraft,
    onDismiss: () -> Unit,
    onSave: (ExerciseDraft) -> Unit,
) {
    var name by remember(initialValue) { mutableStateOf(initialValue.name) }
    var guidance by remember(initialValue) { mutableStateOf(initialValue.guidance) }
    var repMin by remember(initialValue) { mutableStateOf(initialValue.repMin.toString()) }
    var repMax by remember(initialValue) { mutableStateOf(initialValue.repMax.toString()) }
    var loadMin by remember(initialValue) { mutableStateOf(initialValue.loadMin.toString()) }
    var loadMax by remember(initialValue) { mutableStateOf(initialValue.loadMax.toString()) }
    var restTimeSeconds by remember(initialValue) { mutableStateOf(initialValue.restTimeSeconds.toString()) }
    var setCount by remember(initialValue) { mutableStateOf(initialValue.setCount.toString()) }
    var loadKind by remember(initialValue) { mutableStateOf(initialValue.loadKind) }
    var loadUnit by remember(initialValue) { mutableStateOf(initialValue.loadUnit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = guidance, onValueChange = { guidance = it }, label = { Text("Guidance") })
                OutlinedTextField(
                    value = repMin,
                    onValueChange = { repMin = it },
                    label = { Text("Rep min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = repMax,
                    onValueChange = { repMax = it },
                    label = { Text("Rep max") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        loadKind = LoadKind.WEIGHT
                        if (loadUnit == LoadUnit.SEC) loadUnit = LoadUnit.LB
                    }) { Text("Weight") }
                    Button(onClick = {
                        loadKind = LoadKind.DURATION
                        loadUnit = LoadUnit.SEC
                    }) { Text("Time") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { loadUnit = LoadUnit.LB }, enabled = loadKind == LoadKind.WEIGHT) { Text("LB") }
                    Button(onClick = { loadUnit = LoadUnit.KG }, enabled = loadKind == LoadKind.WEIGHT) { Text("KG") }
                    Button(onClick = { loadUnit = LoadUnit.SEC }, enabled = loadKind == LoadKind.DURATION) { Text("SEC") }
                }
                OutlinedTextField(
                    value = loadMin,
                    onValueChange = { loadMin = it },
                    label = { Text("Load min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = loadMax,
                    onValueChange = { loadMax = it },
                    label = { Text("Load max") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = setCount,
                    onValueChange = { setCount = it },
                    label = { Text("Sets") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = restTimeSeconds,
                    onValueChange = { restTimeSeconds = it },
                    label = { Text("Rest (sec)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initialValue.copy(
                            name = name,
                            guidance = guidance,
                            repMin = repMin.toIntOrNull() ?: 0,
                            repMax = repMax.toIntOrNull() ?: 0,
                            loadKind = loadKind,
                            loadMin = loadMin.toDoubleOrNull() ?: 0.0,
                            loadMax = loadMax.toDoubleOrNull() ?: 0.0,
                            loadUnit = loadUnit,
                            restTimeSeconds = restTimeSeconds.toIntOrNull() ?: 0,
                            setCount = setCount.toIntOrNull() ?: 0,
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
