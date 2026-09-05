package com.example.meezan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.meezan.util.Constants
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meezan.MeezanApplication
import com.example.meezan.ui.viewmodel.AnalyticsViewModel
import com.example.meezan.ui.viewmodel.TimeRange
import com.example.meezan.ui.components.MeezanErrorCard
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as MeezanApplication).container
    val viewModel: AnalyticsViewModel = viewModel(
        factory = AnalyticsViewModel.provideFactory(
            appContainer.productivityScoreRepository,
            appContainer.financeRepository,
            appContainer.habitRepository
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            MeezanTopBar(
                title = "Insights",
                onOpenSettings = onOpenSettings,
                actions = {
                    IconButton(onClick = viewModel::retry) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (isLoading) {
                MeezanLoadingBar()
            }
            
            error?.let {
                MeezanErrorCard(
                    message = "Failed to load insights",
                    onRetry = viewModel::retry,
                )
            }

            Spacer(Modifier.height(8.dp))

            // 1. Range Selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedRange == TimeRange.WEEK,
                    onClick = { viewModel.setRange(TimeRange.WEEK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("7 Days") }
                SegmentedButton(
                    selected = selectedRange == TimeRange.MONTH,
                    onClick = { viewModel.setRange(TimeRange.MONTH) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("30 Days") }
            }

            // 2. Highlights
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsSummaryCard("Avg Score", "${state.averageScore.toInt()}%", Icons.AutoMirrored.Filled.TrendingUp, Modifier.weight(1f))
                AnalyticsSummaryCard("Top Category", state.topCategory?.replaceFirstChar { it.uppercase() } ?: "None", Icons.Default.Wallet, Modifier.weight(1f))
            }

            // 3. Productivity Line Chart
            Text("Productivity Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (state.scoreHistory.size >= 2) {
                val entries = remember(state.scoreHistory) { state.scoreHistory.map { it.overallScore } }
                val labelColor = MaterialTheme.colorScheme.onSurface
                Chart(
                    chart = lineChart(lines = listOf(com.patrykandpatrick.vico.core.chart.line.LineChart.LineSpec(lineColor = MaterialTheme.colorScheme.primary.toArgb(), lineThicknessDp = 3f))),
                    model = entryModelOf(*entries.toTypedArray()),
                    startAxis = rememberStartAxis(
                        label = com.patrykandpatrick.vico.compose.component.textComponent(color = labelColor)
                    ),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = { value, _ -> state.dateLabels.getOrNull(value.toInt()) ?: "" },
                        label = com.patrykandpatrick.vico.compose.component.textComponent(color = labelColor)
                    ),
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            } else {
                MeezanEmptyState(
                    message = "Need at least 2 days of activity to show productivity trends.",
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    modifier = Modifier.height(150.dp),
                )
            }

            // 4. Daily Spending Chart
            Text("Daily Spending", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (state.dailySpending.isNotEmpty()) {
                val sorted = remember(state.dailySpending) {
                    state.dailySpending.toList().sortedBy { it.first }.map { it.second }
                }
                val labelColor = MaterialTheme.colorScheme.onSurface
                Chart(
                    chart = columnChart(),
                    model = entryModelOf(*sorted.toTypedArray()),
                    startAxis = rememberStartAxis(
                        label = com.patrykandpatrick.vico.compose.component.textComponent(color = labelColor)
                    ),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = { value, _ -> state.dateLabels.getOrNull(value.toInt()) ?: "" },
                        label = com.patrykandpatrick.vico.compose.component.textComponent(color = labelColor)
                    ),
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            } else {
                MeezanEmptyState(
                    message = "No spending data for this period.",
                    icon = Icons.Default.AccountBalanceWallet,
                    modifier = Modifier.height(150.dp),
                )
            }

            // 5. Heatmap
            Text("Habit Consistency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HabitHeatmap(state.habitHistory)

            if ((state.scoreHistory.size < 2) && state.dailySpending.isEmpty()) {
                MeezanEmptyState(
                    message = "Keep using the app to see deeper insights into your productivity and finances here!",
                    icon = Icons.Default.Insights,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Meezan Insights v1.2.2",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = "Designed & Developed by Uvais Khan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun AnalyticsSummaryCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun HabitHeatmap(logs: List<com.example.meezan.data.entities.HabitLog>) {
    val logsByDate by remember(logs) {
        derivedStateOf {
            logs.groupBy { 
                val cal = Calendar.getInstance().apply { timeInMillis = it.date }
                cal[Calendar.DAY_OF_YEAR] to cal[Calendar.YEAR]
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val now = Calendar.getInstance()
            
            // Start from the 1st of the CURRENT month
            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val monthLabel = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(cal.time)
            Text(monthLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // Weekday Headers
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Text(day, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
            
            Spacer(Modifier.height(4.dp))

            val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 (Sun) to 6 (Sat)
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            
            // Grid of days
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                var dayCounter = 1
                // Up to 6 weeks in a month view
                repeat(6) { weekIndex ->
                    if (dayCounter <= daysInMonth) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            repeat(7) { dayOfWeekIndex ->
                                val isValidDay = (weekIndex > 0 || dayOfWeekIndex >= firstDayOfWeek) && (dayCounter <= daysInMonth)
                                
                                if (isValidDay) {
                                    val currentDayOfYear = cal.get(Calendar.DAY_OF_YEAR)
                                    val currentYear = cal.get(Calendar.YEAR)
                                    
                                    val dayLogs = logsByDate[currentDayOfYear to currentYear] ?: emptyList()
                                    val intensity = if (dayLogs.isEmpty()) 0f else dayLogs.count { it.completed }.toFloat() / dayLogs.size.toFloat()
                                    val isToday = currentDayOfYear == now.get(Calendar.DAY_OF_YEAR) && currentYear == now.get(Calendar.YEAR)

                                    Box(
                                        Modifier
                                            .size(32.dp)
                                            .background(
                                                color = if (intensity == 0f) MaterialTheme.colorScheme.surfaceVariant 
                                                        else MaterialTheme.colorScheme.primary.copy(alpha = intensity.coerceAtLeast(0.2f)), 
                                                shape = MaterialTheme.shapes.small
                                            )
                                            .border(
                                                width = if (isToday) 2.dp else 0.dp,
                                                color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = MaterialTheme.shapes.small
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayCounter.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                                            color = if (intensity > 0.5f) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    dayCounter++
                                    cal.add(Calendar.DAY_OF_MONTH, 1)
                                } else {
                                    Spacer(Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            Text("Monthly progress based on habit logs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}
