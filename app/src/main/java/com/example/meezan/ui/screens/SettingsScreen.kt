package com.example.meezan.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meezan.util.AppError
import com.example.meezan.MeezanApplication
import com.example.meezan.ui.components.MeezanErrorDialog
import com.example.meezan.ui.components.MeezanSnackbarErrorEffect
import com.example.meezan.ui.viewmodel.SettingsViewModel
import com.example.meezan.util.BiometricHelper
import androidx.fragment.app.FragmentActivity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val appContainer = (context.applicationContext as MeezanApplication).container
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(
            appContainer.userPreferencesRepository,
            appContainer.prayerTimingRepository,
            appContainer.dataExportRepository,
            appContainer.fusedLocationClient,
        )
    )

    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarError by viewModel.snackbarError.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showManualLocationDialog by remember { mutableStateOf(false) }
    
    MeezanSnackbarErrorEffect(
        snackbarError = snackbarError,
        snackbarHostState = snackbarHostState,
        onClearError = viewModel::clearSnackbarError
    )
    var showResetDialog by remember { mutableStateOf(false) }
    var includeFinanceInReset by remember { mutableStateOf(false) }
    
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val json = stream.bufferedReader().readText()
                    viewModel.importData(json)
                }
            } catch (e: Exception) { /* handled in VM */ }
        }
    }

    val soundPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.setNotificationSound(uri)
    }

    fun shareFile(file: java.io.File) {
        val uri = FileProvider.getUriForFile(context, "com.example.meezan.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Save Backup"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            if (isLoading) MeezanLoadingBar()

            error?.let { appError ->
                MeezanErrorDialog(
                    error = appError,
                    onDismiss = viewModel::clearError,
                    onAction = {
                        when (appError) {
                            AppError.LocationDisabled -> {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }
                            AppError.NotificationPermissionDenied -> {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                            else -> viewModel.detectLocation() 
                        }
                    }
                )
            }

            success?.let { message ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearSuccess() },
                    title = { Text("Success") },
                    text = { Text(message) },
                    confirmButton = { Button(onClick = { viewModel.clearSuccess() }) { Text("OK") } }
                )
            }

            // 1. APPEARANCE
            SettingsHubCategory(title = "Appearance", icon = Icons.Default.Palette) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("System" to 0, "Light" to 1, "Dark" to 2).forEach { (label, mode) ->
                                FilterChip(
                                    selected = userPreferences.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 2. SPIRITUAL
            SettingsHubCategory(title = "Spiritual", icon = Icons.Default.Mosque) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Madhab (Asr Time)", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = userPreferences.madhab == 1, onClick = { viewModel.setMadhab(1) }, label = { Text("Hanafi") })
                            FilterChip(selected = userPreferences.madhab == 0, onClick = { viewModel.setMadhab(0) }, label = { Text("Shafi/Others") })
                        }
                        
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        
                        Text("Prayer Calculation", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 1, 3, 4, 5, 8).forEach { id ->
                                FilterChip(selected = userPreferences.calculationMethod == id, onClick = { viewModel.setCalculationMethod(id) }, label = { Text(methodLabel(id)) })
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Location", style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = { showManualLocationDialog = true }) { Text("Set Manual") }
                        }
                        Button(onClick = { viewModel.detectLocation() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.MyLocation, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Auto-detect Location")
                        }
                    }
                }
            }

            // 3. NOTIFICATIONS
            SettingsHubCategory(title = "Notifications", icon = Icons.Default.NotificationsActive) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Athan Sound Toggle")
                            Switch(checked = userPreferences.isAthanEnabled, onCheckedChange = { viewModel.setAthanEnabled(it) })
                        }
                        
                        OutlinedButton(onClick = { soundPickerLauncher.launch("audio/*") }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.MusicNote, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Pick Custom Sound")
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.triggerTestAlarm(false) }, Modifier.weight(1f)) { Text("Test Sound") }
                            Button(onClick = { viewModel.triggerTestAlarm(true) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Test Alarm") }
                        }
                    }
                }
            }

            // 4. FINANCE
            SettingsHubCategory(title = "Finance", icon = Icons.Default.AccountBalanceWallet) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Saved Names (Splits)", style = MaterialTheme.typography.labelLarge)
                        if (userPreferences.savedNames.isEmpty()) {
                            Text("No names saved yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                userPreferences.savedNames.forEach { name ->
                                    InputChip(
                                        selected = false,
                                    onClick = { /* Display only */ },
                                    label = { Text(name) },
                                        trailingIcon = { IconButton(onClick = { viewModel.deleteSavedName(name) }, Modifier.size(16.dp)) { Icon(Icons.Default.Close, null) } }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. SYSTEM
            SettingsHubCategory(title = "System & Data", icon = Icons.Default.Dns) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Data Portability", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { viewModel.exportData(context, ::shareFile) }, Modifier.weight(1f)) { 
                                Icon(Icons.Default.Backup, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Backup") 
                            }
                            OutlinedButton(onClick = { openDocumentLauncher.launch(arrayOf("application/json")) }, Modifier.weight(1f)) { 
                                Icon(Icons.Default.Restore, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Restore") 
                            }
                        }
                        
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        
                        OutlinedButton(
                            onClick = {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Check out Meezan - The Balanced Productivity App! \n\nGitHub: https://github.com/Uvais-khan078/mezaan.git")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Share App")
                        }
                    }
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Danger Zone", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text("Permanent actions below", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { showResetDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear All activity Data")
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Meezan v1.2.2 (Premium Build)",
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
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showManualLocationDialog) {
        AlertDialog(
            onDismissRequest = { showManualLocationDialog = false },
            title = { Text("Manual Location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(value = latitude, onValueChange = { latitude = it }, label = { Text("Latitude") }, modifier = Modifier.fillMaxWidth())
                    androidx.compose.material3.OutlinedTextField(value = longitude, onValueChange = { longitude = it }, label = { Text("Longitude") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val lat = latitude.toDoubleOrNull() ?: 0.0
                    val lon = longitude.toDoubleOrNull() ?: 0.0
                    viewModel.setManualLocation(lat, lon)
                    showManualLocationDialog = false
                }) { Text("Save") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Danger Zone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This will delete your goals, habits, and productivity scores. Settings and prayer configurations will be kept.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeFinanceInReset, onCheckedChange = { includeFinanceInReset = it })
                        Text("Include Finance records")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        if (activity != null) {
                            BiometricHelper.authenticate(
                                activity = activity,
                                title = "Confirm Reset",
                                subtitle = "Authenticate to delete data",
                                onSuccess = {
                                    viewModel.clearAllData(includeFinanceInReset)
                                    showResetDialog = false
                                }
                            )
                        } else {
                            viewModel.clearAllData(includeFinanceInReset)
                            showResetDialog = false
                        }
                    }, 
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Selected Data")
                }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SettingsHubCategory(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        content()
    }
}
