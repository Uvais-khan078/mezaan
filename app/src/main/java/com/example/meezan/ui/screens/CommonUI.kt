package com.example.meezan.ui.screens

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.meezan.R
import com.example.meezan.data.entities.GoalTask
import com.example.meezan.data.entities.TaskStatus
import com.example.meezan.util.AppError
import com.example.meezan.util.DateTimeUtil
import kotlinx.coroutines.delay

@Composable
fun TodayTasksCard(
    tasks: List<GoalTask>,
    upcomingTask: GoalTask?,
    onTaskReminderClick: (GoalTask) -> Unit,
    onTaskClick: (Long) -> Unit,
    onSkipTask: (GoalTask) -> Unit,
    onOpenGoalPlanner: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (tasks.isEmpty()) "Upcoming Focus" else "Today's Tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            if (tasks.isEmpty()) {
                if (upcomingTask != null) {
                    Text("Next upcoming focus:", style = MaterialTheme.typography.labelSmall)
                    CommonTaskRow(
                        task = upcomingTask,
                        onTaskReminderClick = { onTaskReminderClick(upcomingTask) },
                        onTaskClick = { onTaskClick(upcomingTask.id) },
                        onSkip = { onSkipTask(upcomingTask) }
                    )
                } else {
                    MeezanEmptyState(
                        message = "No tasks planned yet. Visit the Goal Planner to set your roadmap.",
                        icon = Icons.Default.EventNote
                    )
                }
            } else {
                tasks.forEach { task ->
                    CommonTaskRow(
                        task = task, 
                        onTaskReminderClick = { onTaskReminderClick(task) },
                        onTaskClick = { onTaskClick(task.id) },
                        onSkip = { onSkipTask(task) }
                    )
                }
            }
        }
    }
}

@Composable
fun CommonTaskRow(task: GoalTask, onTaskReminderClick: () -> Unit, onTaskClick: () -> Unit, onSkip: () -> Unit) {
    val statusColor = when (task.status) {
        TaskStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        TaskStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
        TaskStatus.SKIPPED -> MaterialTheme.colorScheme.outline
        TaskStatus.NOT_COMPLETED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        onClick = onTaskClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.taskName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("Day ${task.dayNumber} • ${task.requiredDurationMinutes} min", style = MaterialTheme.typography.bodySmall)
                }
                StatusPill(task.status.name, statusColor)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (task.reminderTime != null) "Reminder: ${DateTimeUtil.formatTimeFromMillis(task.reminderTime % (24 * 60 * 60 * 1000))}" else "No reminder",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (task.status != TaskStatus.SKIPPED) {
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) { Text("Skip", color = MaterialTheme.colorScheme.outline) }
                    }
                    IconButton(
                        onClick = onTaskReminderClick,
                        modifier = Modifier.size(40.dp)
                    ) { Icon(Icons.Default.Notifications, null) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeezanTopBar(
    title: String,
    onOpenSettings: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isOffline = rememberConnectivityStatus(context)

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenSettings() 
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Balance,
                    contentDescription = "App Logo - Open Settings",
                    modifier = Modifier.size(32.dp).padding(end = 8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(title, fontWeight = FontWeight.ExtraBold)
                
                if (isOffline) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Offline Mode",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        actions = {
            actions()
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenSettings()
            }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    )
}

@Composable
fun rememberConnectivityStatus(context: android.content.Context): Boolean {
    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return true
    val capabilities = cm.getNetworkCapabilities(network) ?: return true
    return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
fun StatusPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f), 
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Text(text = text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PulseCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    val animationsEnabled = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }

    val scale by animateFloatAsState(
        targetValue = if (checked && animationsEnabled) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "checkboxPulse"
    )

    Checkbox(
        checked = checked,
        onCheckedChange = {
            if (it) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onCheckedChange(it)
        },
        modifier = modifier.scale(scale)
    )
}

@Composable
fun CountdownText(nextPrayer: com.example.meezan.ui.viewmodel.PrayersViewModel.NextPrayerInfo?) {
    if (nextPrayer == null) return
    
    var countdown by remember(nextPrayer.timeUntilMs) { mutableLongStateOf(nextPrayer.timeUntilMs) }
    
    LaunchedEffect(nextPrayer.timeUntilMs) {
        while (countdown > 0) {
            delay(1000)
            countdown -= 1000
        }
    }
    
    Text(
        text = "Next: ${nextPrayer.name} in ${DateTimeUtil.formatCountdown(countdown)}",
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold
    )
}

fun methodLabel(method: Int): String = when (method) {
    1 -> "Karachi"
    2 -> "ISNA"
    3 -> "MWL"
    4 -> "Makkah"
    5 -> "Egypt"
    8 -> "Gulf"
    else -> method.toString()
}

@Composable
fun MeezanEmptyState(
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MeezanLoadingBar(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    )
}
