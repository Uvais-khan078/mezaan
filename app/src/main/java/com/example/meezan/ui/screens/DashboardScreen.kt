package com.example.meezan.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meezan.MeezanApplication
import com.example.meezan.data.entities.PrayerReminderRule
import com.example.meezan.ui.components.MeezanErrorDialog
import com.example.meezan.ui.components.MeezanSnackbarErrorEffect
import com.example.meezan.ui.viewmodel.DashboardViewModel
import com.example.meezan.ui.viewmodel.PrayersViewModel
import com.example.meezan.util.AppError
import com.example.meezan.util.BiometricHelper
import com.example.meezan.util.DateTimeUtil
import androidx.fragment.app.FragmentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenSettings: () -> Unit,
    onOpenFinance: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val appContainer = (context.applicationContext as MeezanApplication).container
    val viewModel: PrayersViewModel = viewModel(
        factory = PrayersViewModel.provideFactory(
            appContainer.prayerTimingRepository,
            appContainer.userPreferencesRepository,
        ),
    )
    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.provideFactory(
            appContainer.productivityScoreRepository,
            appContainer.financeRepository,
        ),
    )

    val prayerTiming by viewModel.prayerTiming.collectAsStateWithLifecycle()
    val prayerLogs by viewModel.prayerLogs.collectAsStateWithLifecycle()
    val nextPrayerInfo by viewModel.nextPrayerInfo.collectAsStateWithLifecycle()
    val activeIndex by viewModel.activePrayerIndex.collectAsStateWithLifecycle()
    
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle() // Actually settings might be better here but VM has them too for now
    
    val dailyScore by dashboardViewModel.dailyScore.collectAsStateWithLifecycle()
    val financeProfile by dashboardViewModel.financeProfile.collectAsStateWithLifecycle()
    val todaysSpent by dashboardViewModel.todaysSpent.collectAsStateWithLifecycle()
    
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarError by viewModel.snackbarError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showPrayerSettings by remember { mutableStateOf<Pair<String, Long>?>(value = null) }
    val sheetState = rememberModalBottomSheetState()

    MeezanSnackbarErrorEffect(
        snackbarError = snackbarError,
        snackbarHostState = snackbarHostState,
        onClearError = viewModel::clearSnackbarError,
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.retryFetch()
        }
    }

    Scaffold(
        topBar = {
            MeezanTopBar(
                title = "Meezan",
                onOpenSettings = onOpenSettings,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        val hasLocation = (userPreferences.latitude != 0.0) || (userPreferences.longitude != 0.0)
        
        if (!hasLocation && !isLoading && (prayerTiming == null)) {
            SetupScreen(
                error = error,
                onDismissError = viewModel::clearError,
                onLocationSet = { lat, lon ->
                    viewModel.fetchPrayerTimingsForLocation(lat, lon, userPreferences.calculationMethod)
                },
                onAutoDetect = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                modifier = Modifier.padding(paddingValues),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isLoading) {
                    item { MeezanLoadingBar() }
                }

                error?.let { appError ->
                    item {
                        MeezanErrorDialog(
                            error = appError,
                            onDismiss = viewModel::clearError,
                            onAction = viewModel::retryFetch,
                        )
                    }
                }

                // Score Card
                dailyScore?.let { score ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Productivity Score", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text = "${score.overallScore.toInt()}%",
                                        style = MaterialTheme.typography.displayLarge,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    CountdownText(nextPrayerInfo)
                                }

                                Box(contentAlignment = Alignment.Center) {
                                    val animatedProgress by animateFloatAsState(targetValue = score.overallScore / 100f, label = "score")
                                    CircularProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier.size(80.dp),
                                        strokeWidth = 8.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                    Text("${score.overallScore.toInt()}%", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Today's Prayer Header
                item {
                    Text(
                        text = "Today's Schedule",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }

                // Prayer List
                prayerTiming?.let { timing ->
                    val schedule = listOf(
                        "Fajr" to (timing.fajrStart to Icons.Default.WbTwilight),
                        "Sunrise" to (timing.sunrise to Icons.Default.LightMode),
                        "Dhuhr" to (timing.dhuhrStart to Icons.Default.WbSunny),
                        "Asr" to (timing.asrStart to Icons.Default.WbSunny),
                        "Sunset" to (timing.sunset to Icons.Default.WbTwilight),
                        "Maghrib" to (timing.maghribStart to Icons.Default.WbTwilight),
                        "Isha" to (timing.ishaStart to Icons.Default.NightsStay)
                    )

                    items(schedule.size, key = { schedule[it].first }) { index ->
                        val (name, pair) = schedule[index]
                        val (timeMillis, icon) = pair
                        val isPrayer = name !in listOf("Sunrise", "Sunset")
                        val isCompleted = if (isPrayer) prayerLogs.find { it.prayerName == name }?.completed ?: false else false
                        
                        // Map the list index (0-6) back to the prayer index (0-4)
                        val prayerIndex = schedule.subList(0, index + 1).count { it.first !in listOf("Sunrise", "Sunset") } - 1
                        val isNextActive = (activeIndex == -1 && name == "Fajr")
                        val isActive = isPrayer && (prayerIndex == activeIndex || isNextActive)

                        Box(
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .animateItem(),
                        ) {
                            PrayerItemRow(
                                name = name,
                                time = DateTimeUtil.formatTimeFromMillis(timeMillis),
                                icon = icon,
                                isCompleted = isCompleted,
                                isActive = isActive,
                                showCheckbox = false, // Always false now as per user request
                                onToggle = { viewModel.togglePrayer(name, it) },
                                onClick = { if (isPrayer) showPrayerSettings = name to timeMillis },
                            )
                        }
                    }
                }

                // Finance At-a-glance (MOVED BELOW PRAYERS & REDESIGNED)
                financeProfile?.let { profile ->
                    item {
                        val left = (profile.dailyLimit - todaysSpent).coerceAtLeast(0.0)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clickable {
                                    if (activity != null) {
                                        BiometricHelper.authenticate(
                                            activity = activity,
                                            title = "Finance Access",
                                            onSuccess = onOpenFinance
                                        )
                                    } else {
                                        onOpenFinance()
                                    }
                                },
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.secondary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        )
                                    )
                                    .padding(24.dp)
                                    .fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Text(
                                            "Daily Budget Left",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = "₹${left.toInt()}",
                                            style = MaterialTheme.typography.displayMedium,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSecondary
                                        )
                                        Text(
                                            "Tap to manage finances",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.6f)
                                        )
                                    }
                                    Icon(
                                        Icons.Default.AccountBalanceWallet,
                                        null,
                                        modifier = Modifier.size(48.dp).alpha(0.2f),
                                        tint = MaterialTheme.colorScheme.onSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(48.dp)) }
            }
        }
    }

    showPrayerSettings?.let { settings ->
        ModalBottomSheet(onDismissRequest = { showPrayerSettings = null }, sheetState = sheetState) {
            val (name, baseTime) = settings
            val rules by viewModel.reminderRules.collectAsStateWithLifecycle()
            val rule = rules.find { it.prayerName == name }
            val logs by viewModel.prayerLogs.collectAsStateWithLifecycle()
            val isDone = logs.find { it.prayerName == name }?.completed ?: false

            PrayerSettingsPanel(
                prayerName = name,
                baseTimeMillis = baseTime,
                isCompleted = isDone,
                currentRule = rule,
                onToggleCompleted = { viewModel.togglePrayer(name, it) },
                onSave = { anchor, offset, isAlarm ->
                    viewModel.saveReminderRule(name, anchor, offset, isAlarm)
                    showPrayerSettings = null
                },
                onDelete = {
                    rule?.let { viewModel.deleteReminderRule(it) }
                    showPrayerSettings = null
                },
                onTest = { viewModel.testAlarm(name) }
            )
        }
    }
}

@Composable
fun PrayerItemRow(
    name: String,
    time: String,
    icon: ImageVector,
    isCompleted: Boolean,
    isActive: Boolean,
    showCheckbox: Boolean = true,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (isActive) 2.dp else 1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCheckbox) {
                PulseCheckbox(checked = isCompleted, onCheckedChange = onToggle)
                Spacer(Modifier.width(12.dp))
            } else {
                Spacer(Modifier.width(48.dp)) // Alignment offset for non-checkbox items
            }
            Icon(icon, null, tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(time, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PrayerSettingsPanel(
    prayerName: String,
    baseTimeMillis: Long,
    isCompleted: Boolean,
    currentRule: PrayerReminderRule?,
    onToggleCompleted: (Boolean) -> Unit,
    onSave: (String, Int, Boolean) -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    var anchor by remember { mutableStateOf(currentRule?.anchor ?: "START") }
    var isAlarm by remember { mutableStateOf(true) } // For namaz, it's always an alarm now
    
    // Convert offset to sign, hours, minutes
    val initialOffset = currentRule?.offsetMinutes ?: 0
    var isPositive by remember { mutableStateOf(initialOffset >= 0) }
    var hours by remember { mutableStateOf(Math.abs(initialOffset) / 60) }
    var minutes by remember { mutableStateOf(Math.abs(initialOffset) % 60) }

    // Calculate alarm preview time
    val alarmTimeMillis = remember(baseTimeMillis, isPositive, hours, minutes) {
        val offset = (hours * 60 + minutes) * (if (isPositive) 1 else -1)
        baseTimeMillis + (offset * 60 * 1000L)
    }

    Column(
        Modifier.padding(horizontal = 24.dp, vertical = 32.dp).fillMaxWidth(), 
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "When should the $prayerName reminder be sent?", 
            style = MaterialTheme.typography.titleLarge, 
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Sign Wheel (+ / -)
            SignWheelPicker(
                isPositive = isPositive,
                onValueChange = { isPositive = it }
            )

            // 2. Hours Wheel
            TimeWheelPicker(
                value = hours,
                onValueChange = { hours = it },
                range = 0..23,
                label = "HR"
            )

            Text(":", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(bottom = 20.dp))

            // 3. Minutes Wheel
            TimeWheelPicker(
                value = minutes,
                onValueChange = { minutes = it },
                range = 0..59,
                label = "MIN"
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = { 
                    val totalMinutes = (hours * 60 + minutes) * (if (isPositive) 1 else -1)
                    onSave(anchor, totalMinutes, isAlarm) 
                }, 
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) { 
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Set Alarm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) 
                    Text(
                        text = "(${DateTimeUtil.formatTimeFromMillis(alarmTimeMillis % (24 * 60 * 60 * 1000L))})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onTest,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.BugReport, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Test Now")
                }

                if (currentRule != null) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f)
                    ) { 
                        Text("Remove", color = MaterialTheme.colorScheme.error) 
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SignWheelPicker(
    isPositive: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = if (isPositive) 0 else 1)
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val itemHeight = 44.dp

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            onValueChange(firstVisibleItemIndex == 0)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .height(itemHeight * 3) // Show 3 items: 1 selected, 2 partial
                .width(70.dp),
            contentAlignment = Alignment.Center
        ) {
            // Selection background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), MaterialTheme.shapes.small)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = itemHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
            ) {
                items(2) { index ->
                    val sign = if (index == 0) "+" else "-"
                    val isSelected = index == firstVisibleItemIndex
                    Box(
                        modifier = Modifier.height(itemHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sign,
                            style = if (isSelected) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            // Divider lines - precisely placed
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Spacer(Modifier.height(itemHeight))
                HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                Spacer(Modifier.weight(1f))
                HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                Spacer(Modifier.height(itemHeight))
            }
        }
        Text("SIGN", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun TimeWheelPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = value + (500 / (range.last + 1) * (range.last + 1)))
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val itemHeight = 44.dp

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            onValueChange(firstVisibleItemIndex % (range.last + 1))
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .height(itemHeight * 3)
                .width(80.dp),
            contentAlignment = Alignment.Center
        ) {
            // Selection background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), MaterialTheme.shapes.small)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = itemHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
            ) {
                items(1000) { index ->
                    val displayVal = index % (range.last + 1)
                    val isSelected = index == firstVisibleItemIndex
                    Box(
                        modifier = Modifier.height(itemHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "%02d".format(displayVal),
                            style = if (isSelected) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            // Divider lines - precisely placed
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Spacer(Modifier.height(itemHeight))
                HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                Spacer(Modifier.weight(1f))
                HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                Spacer(Modifier.height(itemHeight))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun SetupScreen(
    error: AppError?,
    onDismissError: () -> Unit,
    onLocationSet: (Double, Double) -> Unit, 
    onAutoDetect: () -> Unit, 
    modifier: Modifier = Modifier
) {
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Welcome to Meezan", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Latitude") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = lon, onValueChange = { lon = it }, label = { Text("Longitude") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onLocationSet(lat.toDoubleOrNull() ?: 0.0, lon.toDoubleOrNull() ?: 0.0) }, 
            modifier = Modifier.fillMaxWidth(),
            enabled = lat.isNotBlank() && lon.isNotBlank()
        ) { Text("Set Manual") }
        TextButton(onClick = onAutoDetect) { Text("Auto-detect Location") }

        error?.let {
            MeezanErrorDialog(
                error = it,
                onDismiss = onDismissError,
                onAction = {
                    if (it is AppError.NoInternet || it is AppError.ApiError) {
                        // For connectivity/API errors on first setup, guide to manual
                        onDismissError()
                    } else {
                        onAutoDetect()
                    }
                }
            )
            
            if (it is AppError.NoInternet || it is AppError.ApiError) {
                Text(
                    text = "You can still proceed by entering your coordinates manually below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
