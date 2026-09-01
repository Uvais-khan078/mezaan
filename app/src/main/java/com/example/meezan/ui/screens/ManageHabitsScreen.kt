package com.example.meezan.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meezan.MeezanApplication
import com.example.meezan.ui.viewmodel.HabitViewModel
import com.example.meezan.ui.components.MeezanErrorDialog
import com.example.meezan.ui.components.MeezanSnackbarErrorEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageHabitsScreen(
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as MeezanApplication).container
    val viewModel: HabitViewModel = viewModel(
        factory = HabitViewModel.provideFactory(
            appContainer.habitRepository,
            appContainer.productivityScoreRepository,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarError by viewModel.snackbarError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(value = false) }

    MeezanSnackbarErrorEffect(
        snackbarError = snackbarError,
        snackbarHostState = snackbarHostState,
        onClearError = viewModel::clearSnackbarError,
    )
    var habitToDelete by remember { mutableStateOf<com.example.meezan.data.entities.Habit?>(value = null) }

    Scaffold(
        topBar = {
            MeezanTopBar(
                title = "Habits",
                onOpenSettings = onOpenSettings,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Habit")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            
            item {
                Text("Today's Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            if (state.habits.isEmpty()) {
                item {
                    MeezanEmptyState(
                        message = "Consistency is key. Add your first habit and start building streaks!",
                        icon = Icons.Default.Repeat,
                    )
                }
            } else {
                items(state.habits, key = { it.habit.id }) { habitWithHistory ->
                    var showTimePicker by remember { mutableStateOf(value = false) }
                    val isCompleted = state.todayLogs.find { it.habitId == habitWithHistory.habit.id }?.completed ?: false
                    
                    Box(Modifier.animateItem()) {
                        HabitCheckItem(
                            name = habitWithHistory.habit.name,
                            streak = habitWithHistory.streak,
                            isCompleted = isCompleted,
                            reminderTime = habitWithHistory.habit.reminderTime,
                            isDeletable = habitWithHistory.habit.isDeletable,
                            onToggle = { viewModel.toggleHabit(habitWithHistory.habit.id, it) },
                            onSettings = { showTimePicker = true },
                            onDelete = { habitToDelete = habitWithHistory.habit },
                        )
                    }

                    if (showTimePicker) {
                        HabitTimePickerDialog(
                            onDismiss = { showTimePicker = false },
                            onTimeSelected = { h, m ->
                                viewModel.setHabitReminder(habitWithHistory.habit.id, h, m, true)
                                showTimePicker = false
                            }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        if (showAddDialog) {
            AddHabitDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { name ->
                    viewModel.addHabit(name)
                    showAddDialog = false
                }
            )
        }

        if (habitToDelete != null) {
            AlertDialog(
                onDismissRequest = { habitToDelete = null },
                title = { Text("Delete Habit?") },
                text = { Text("This will permanently remove the habit and your streak history.") },
                confirmButton = { 
                    Button(
                        onClick = { viewModel.deleteHabit(habitToDelete!!); habitToDelete = null },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { habitToDelete = null }) { Text("Cancel") } }
            )
        }

        error?.let { appError ->
            MeezanErrorDialog(
                error = appError,
                onDismiss = viewModel::clearError,
                onAction = { viewModel.clearError() }
            )
        }
    }
}

@Composable
fun HabitCheckItem(
    name: String,
    streak: Int,
    isCompleted: Boolean,
    reminderTime: Long?,
    isDeletable: Boolean = true,
    onToggle: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                PulseCheckbox(checked = isCompleted, onCheckedChange = onToggle)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (streak > 0) {
                            Text("🔥 $streak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (reminderTime != null) {
                            Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = com.example.meezan.util.DateTimeUtil.formatTimeFromMillis(reminderTime),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
            Row {
                IconButton(onClick = onSettings) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp)) }
                if (isDeletable) {
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitTimePickerDialog(
    onDismiss: () -> Unit,
    onTimeSelected: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder Time") },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = { TextButton(onClick = { onTimeSelected(timePickerState.hour, timePickerState.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddHabitDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var habitName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Habit") },
        text = {
            OutlinedTextField(
                value = habitName,
                onValueChange = { habitName = it },
                label = { Text("Name (e.g. Exercise, Read)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { Button(onClick = { if (habitName.isNotBlank()) onAdd(habitName) }, enabled = habitName.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
