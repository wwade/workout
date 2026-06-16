package dev.wwade.workout.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import dev.wwade.workout.ui.state.ActiveSessionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    state: ActiveSessionState,
    onBack: () -> Unit,
    onUpdateReps: (Long, String) -> Unit,
    onUpdateLoad: (Long, String) -> Unit,
    onUpdateNotes: (Long, String) -> Unit,
    onUpdateSkipped: (Long, Boolean) -> Unit,
    onPreviousRound: () -> Unit,
    onNextRound: () -> Unit,
    onSelectRound: (Int, Int) -> Unit,
    onSaveRound: () -> Unit,
    onAbandonSession: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val exerciseIds = state.exerciseCards.map { it.exerciseSessionId }
    val repsFocusRequesters = remember(exerciseIds) { exerciseIds.map { FocusRequester() } }
    val loadFocusRequesters = remember(exerciseIds) { exerciseIds.map { FocusRequester() } }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentCircuitIndex, state.currentSetIndex, state.isCompleted) {
        if (!state.isCompleted) {
            listState.scrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.workoutName.ifBlank { "Active session" }) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = onAbandonSession) { Text("Abandon") }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row {
                        IconButton(onClick = onPreviousRound, enabled = state.canGoBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous set",
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Box {
                            TextButton(
                                onClick = { menuExpanded = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Text("${state.currentCircuitName} - Set ${state.currentSetIndex + 1}")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                state.positionOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(option.label)
                                                Text(
                                                    option.statusLabel,
                                                    color = if (option.isSelectable) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                )
                                            }
                                        },
                                        enabled = option.isSelectable,
                                        onClick = {
                                            menuExpanded = false
                                            onSelectRound(option.circuitIndex, option.setIndex)
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = onNextRound, enabled = state.canGoForward) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next set",
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Set ${state.currentSetIndex + 1} of ${state.totalSetsInCircuit}")
                        val currentStatus = state.positionOptions.firstOrNull {
                            it.circuitIndex == state.currentCircuitIndex && it.setIndex == state.currentSetIndex
                        }?.statusLabel
                        if (currentStatus != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = currentStatus,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onSaveRound, enabled = state.canSaveRound) {
                    Text(if (state.isLastRound && state.isLastCircuit) "Finish" else "Save round")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("exercise-list")
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.errorMessage?.let { error ->
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            itemsIndexed(state.exerciseCards, key = { _, exercise -> exercise.exerciseSessionId }) { index, exercise ->
                if (index == 0) {
                    LaunchedEffect(state.currentCircuitIndex, state.currentSetIndex, exercise.exerciseSessionId) {
                        if (!state.isCompleted) {
                            withFrameNanos { }
                            repsFocusRequesters[index].requestFocus()
                        }
                    }
                }
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium)
                        if (exercise.guidance.isNotBlank()) {
                            Text(exercise.guidance)
                        }
                        Text("Target reps: ${exercise.repRangeLabel}")
                        Text("Target load: ${exercise.loadRangeLabel}")
                        Text("Rest: ${exercise.restTimeSeconds} sec")
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(
                                checked = exercise.skipped,
                                onCheckedChange = { onUpdateSkipped(exercise.exerciseSessionId, it) },
                            )
                            Text("Skip this set")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SelectAllTextField(
                                value = exercise.repsInput,
                                onValueChange = { onUpdateReps(exercise.exerciseSessionId, it) },
                                label = { Text("Reps") },
                                resetKey = "${state.currentCircuitIndex}:${state.currentSetIndex}:${exercise.exerciseSessionId}:reps",
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(repsFocusRequesters[index])
                                    .testTag("reps-${exercise.exerciseSessionId}"),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = {
                                        runCatching { loadFocusRequesters[index].requestFocus() }
                                            .getOrElse { focusManager.moveFocus(FocusDirection.Next) }
                                    },
                                ),
                                enabled = !exercise.skipped,
                            )
                            SelectAllTextField(
                                value = exercise.loadInput,
                                onValueChange = { onUpdateLoad(exercise.exerciseSessionId, it) },
                                label = { Text("Load (${exercise.loadUnit.name.lowercase()})") },
                                resetKey = "${state.currentCircuitIndex}:${state.currentSetIndex}:${exercise.exerciseSessionId}:load",
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(loadFocusRequesters[index])
                                    .testTag("load-${exercise.exerciseSessionId}"),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = {
                                        val nextIndex = index + 1
                                        if (nextIndex < state.exerciseCards.size) {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(nextIndex)
                                                withFrameNanos { }
                                                runCatching { repsFocusRequesters[nextIndex].requestFocus() }
                                                    .getOrElse { focusManager.clearFocus() }
                                            }
                                        } else {
                                            focusManager.clearFocus()
                                        }
                                    },
                                ),
                                enabled = !exercise.skipped,
                            )
                        }
                        OutlinedTextField(
                            value = exercise.notesInput,
                            onValueChange = { onUpdateNotes(exercise.exerciseSessionId, it) },
                            label = { Text("Notes") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectAllTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    resetKey: Any,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    enabled: Boolean,
) {
    var fieldValue by remember(resetKey) { mutableStateOf(TextFieldValue(value)) }
    var wasFocused by remember(resetKey) { mutableStateOf(false) }

    LaunchedEffect(value, resetKey) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            onValueChange(it.text)
        },
        label = label,
        modifier = modifier.onFocusChanged { focusState ->
            if (focusState.isFocused && !wasFocused && fieldValue.text.isNotEmpty()) {
                fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
            }
            wasFocused = focusState.isFocused
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        enabled = enabled,
    )
}
