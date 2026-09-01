package com.example.meezan.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
                Chart(
                    chart = lineChart(lines = listOf(com.patrykandpatrick.vico.core.chart.line.LineChart.LineSpec(lineColor = MaterialTheme.colorScheme.primary.toArgb()))),
                    model = entryModelOf(*entries.toTypedArray()),
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(),
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            } else {
                MeezanEmptyState(
                    message = "Need at least 2 days of activity to show productivity trends.",
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    modifier = Modifier.height(150.dp),
                )
            }

            // 4. Spending Column Chart
            Text("Daily Spending", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (state.dailySpending.isNotEmpty()) {
                val sorted = remember(state.dailySpending) {
                    state.dailySpending.toList().sortedBy { it.first }.map { it.second }
                }
                Chart(
                    chart = columnChart(),
                    model = entryModelOf(*sorted.toTypedArray()),
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(),
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

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun AnalyticsSummaryCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
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
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -34)
            
            val monthLabel = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(cal.time)
            Text(monthLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until Constants.Analytics.HEATMAP_WEEKS).forEach { _ ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(Constants.Analytics.DAYS_IN_WEEK) {
                            val currentDay = cal.get(Calendar.DAY_OF_YEAR)
                            val currentYear = cal.get(Calendar.YEAR)
                            val dayLogs = logsByDate[currentDay to currentYear] ?: emptyList()
                            val intensity = if (dayLogs.isEmpty()) 0f else dayLogs.count { it.completed }.toFloat() / dayLogs.size.toFloat()
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .background(
                                        color = if (intensity == 0f) MaterialTheme.colorScheme.surfaceVariant 
                                                else MaterialTheme.colorScheme.primary.copy(alpha = intensity.coerceAtLeast(0.2f)), 
                                        shape = MaterialTheme.shapes.extraSmall
                                    )
                            )
                            cal.add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Consistency grid (last 5 weeks)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
