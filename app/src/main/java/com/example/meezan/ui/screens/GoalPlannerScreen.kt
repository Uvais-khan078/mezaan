package com.example.meezan.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import com.example.meezan.data.entities.TaskStatus
import com.example.meezan.ui.components.MeezanErrorDialog
import com.example.meezan.ui.components.MeezanSnackbarErrorEffect
import com.example.meezan.ui.viewmodel.GoalPlannerViewModel
import com.example.meezan.util.AppError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalPlannerScreen(
    onOpenSettings: () -> Unit,
    onStartTask: (Long) -> Unit,
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as MeezanApplication).container
    val viewModel: GoalPlannerViewModel = viewModel(
        factory = GoalPlannerViewModel.provideFactory(appContainer.goalRepository),
    )
    
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val todayTasks by viewModel.todayTasks.collectAsStateWithLifecycle()
    val upcomingTask by viewModel.upcomingTask.collectAsStateWithLifecycle()
    val snackbarError by viewModel.snackbarError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var goalToDelete by remember { mutableStateOf<Long?>(value = null) }

    MeezanSnackbarErrorEffect(
        snackbarError = snackbarError,
        snackbarHostState = snackbarHostState,
        onClearError = viewModel::clearSnackbarError,
    )

    Scaffold(
        topBar = {
            MeezanTopBar(
                title = "Goals",
                onOpenSettings = onOpenSettings,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            
            TodayTasksCard(
                tasks = todayTasks,
                upcomingTask = upcomingTask,
                onTaskReminderClick = { viewModel.setTaskReminder(it, 19, 0) },
                onTaskClick = onStartTask,
                onSkipTask = { viewModel.updateTaskStatus(it, TaskStatus.SKIPPED) },
                onOpenGoalPlanner = { /* Already on this screen */ },
            )

            HorizontalDivider()

            Text("Plan New Goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = state.inputText,
                onValueChange = viewModel::onInputChange,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                placeholder = { Text("Paste roadmap text...") },
                shape = MaterialTheme.shapes.large,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { 
                        viewModel.onInputChange("Goal: Work Out\n\nDay 1\nTask: 20 min Yoga\nDuration: 20m\n\nDay 2\nTask: Cardio\nDuration: 30m") 
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Template") }
                
                Button(onClick = viewModel::parseRoadmap, modifier = Modifier.weight(1f)) { Text("Add roadmap") }
            }

            if (state.activeGoals.isNotEmpty()) {
                Text("Manage Goals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                state.activeGoals.forEach { goal ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(goal.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { goalToDelete = goal.id }) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            state.error?.let { appError ->
                MeezanErrorDialog(
                    error = appError,
                    onDismiss = viewModel::clearGoalError,
                    onAction = {
                        if (appError is AppError.RoadmapParseError) {
                            // Logic to scroll to field or something? For now just dismiss
                            viewModel.clearGoalError()
                        } else {
                            viewModel.parseRoadmap()
                        }
                    },
                )
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }

    if (goalToDelete != null) {
        AlertDialog(
            onDismissRequest = { goalToDelete = null },
            title = { Text("Delete Goal?") },
            text = { Text("This will remove the goal and all associated tasks forever.") },
            confirmButton = { 
                Button(
                    onClick = { viewModel.deleteGoal(goalToDelete!!); goalToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { goalToDelete = null }) { Text("Cancel") } },
        )
    }

    if (state.showSaveConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSaveConfirmation,
            title = { Text("Confirm Plan") },
            text = { Text("Save this goal and its tasks to your daily list?") },
            confirmButton = { Button(onClick = viewModel::confirmSaveParsedRoadmap) { Text("Save") } },
            dismissButton = { TextButton(onClick = viewModel::dismissSaveConfirmation) { Text("Cancel") } },
        )
    }
}
