package com.example.meezan

import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.meezan.ui.screens.*
import com.example.meezan.ui.theme.MeezanTheme
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : FragmentActivity() {
    private var isReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        val appContainer = (applicationContext as MeezanApplication).container
        splashScreen.setKeepOnScreenCondition { !isReady }

        enableEdgeToEdge()
        setContent {
            var startupError by remember { mutableStateOf<String?>(null) }

            // Safety timeout for Splash Screen: Release white screen if prefs don't load
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(4000.milliseconds)
                isReady = true
            }

            val userPrefsResult = remember {
                try {
                    appContainer.userPreferencesRepository.userPreferencesFlow
                } catch (e: Throwable) {
                    startupError = "Initialization Failed: ${e.localizedMessage}"
                    null
                }
            }

            val userPrefs by (userPrefsResult ?: kotlinx.coroutines.flow.emptyFlow())
                .collectAsStateWithLifecycle(initialValue = null)

            LaunchedEffect(userPrefs) {
                if (userPrefs != null) {
                    isReady = true
                }
            }

            if (startupError != null) {
                MeezanTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer) {
                        Column(
                            Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Meezan couldn't start", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(startupError!!, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { this@MainActivity.finish() }) { Text("Close App") }
                        }
                    }
                }
                return@setContent
            }

            if ((userPrefs == null) && !isReady) return@setContent

            val darkTheme = when (userPrefs?.themeMode) {
                1 -> false // Light
                2 -> true  // Dark
                else -> isSystemInDarkTheme()
            }

            val context = LocalContext.current
            val animationsEnabled = remember {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
            }

            MeezanTheme(darkTheme = darkTheme) {
                var currentTab by rememberSaveable { mutableStateOf(MeezanTab.PRAYERS) }
                var showSettings by rememberSaveable { mutableStateOf(value = false) }
                var selectedTaskId by remember { mutableLongStateOf(-1L) }
                var currentScreen by rememberSaveable { mutableStateOf("main") }

                val haptic = LocalHapticFeedback.current

                // Apply FLAG_SECURE when on Finance tab
                DisposableEffect(currentTab) {
                    if (currentTab == MeezanTab.FINANCE) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                    onDispose {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                fun showBiometricPrompt(onSuccess: () -> Unit) {
                    val executor: Executor = ContextCompat.getMainExecutor(this@MainActivity)
                    val biometricPrompt = BiometricPrompt(
                        this@MainActivity,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                super.onAuthenticationSucceeded(result)
                                onSuccess()
                            }
                        },
                    )

                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Finance Secure Access")
                        .setSubtitle("Authenticate to view your financial data")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        .build()

                    biometricPrompt.authenticate(promptInfo)
                }

                // Handle system back button
                BackHandler {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (showSettings) {
                        showSettings = false
                    } else if (currentScreen != "main") {
                        currentScreen = "main"
                    } else if (currentTab != MeezanTab.PRAYERS) {
                        currentTab = MeezanTab.PRAYERS
                    } else {
                        this@MainActivity.finish()
                    }
                }

                Scaffold(
                    bottomBar = {
                        if (currentScreen == "main" && !showSettings) {
                            NavigationBar {
                                MeezanTab.entries.forEach { tab ->
                                    NavigationBarItem(
                                        selected = currentTab == tab,
                                        onClick = { 
                                            if (currentTab != tab) {
                                                if (tab == MeezanTab.FINANCE) {
                                                    showBiometricPrompt {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        currentTab = tab
                                                    }
                                                } else {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    currentTab = tab
                                                }
                                            }
                                        },
                                        label = { Text(tab.label) },
                                        icon = { Icon(tab.icon, contentDescription = tab.label) }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(Modifier.padding(innerPadding)) {
                            AnimatedContent(
                                targetState = if (showSettings) "settings" else if (currentScreen == "focus_timer") "timer" else currentTab.name,
                                transitionSpec = {
                                    if (animationsEnabled) {
                                        fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                                    } else {
                                        EnterTransition.None togetherWith ExitTransition.None
                                    }
                                },
                                label = "TabTransition"
                            ) { target ->
                                when (target) {
                                    "settings" -> SettingsScreen { showSettings = false }
                                    "timer" -> FocusTimerScreen(taskId = selectedTaskId, onBack = { currentScreen = "main" })
                                    MeezanTab.PRAYERS.name -> DashboardScreen(
                                        onOpenSettings = { showSettings = true },
                                        onOpenFinance = { currentTab = MeezanTab.FINANCE }
                                    )
                                    MeezanTab.GOALS.name -> GoalPlannerScreen(onOpenSettings = { showSettings = true }, onStartTask = { id -> selectedTaskId = id; currentScreen = "focus_timer" })
                                    MeezanTab.HABITS.name -> ManageHabitsScreen(onOpenSettings = { showSettings = true })
                                    MeezanTab.FINANCE.name -> FinanceManagerScreen(onOpenSettings = { showSettings = true })
                                    MeezanTab.INSIGHTS.name -> AnalyticsScreen(onOpenSettings = { showSettings = true })
                                }
                            }
                        }

                        if (intent.getBooleanExtra("FATAL_ERROR", false)) {
                            AlertDialog(
                                onDismissRequest = { intent.removeExtra("FATAL_ERROR") },
                                title = { Text("Oops! Something went wrong", fontWeight = FontWeight.Bold) },
                                text = { Text("Meezan encountered an unexpected error but managed to recover. Your data is safe. We've logged the details to help us fix it.") },
                                confirmButton = { 
                                    Button(onClick = { intent.removeExtra("FATAL_ERROR") }) { Text("Got it") } 
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class MeezanTab(val label: String, val icon: ImageVector) {
    PRAYERS("Prayers", Icons.Default.Mosque),
    GOALS("Goals", Icons.Default.GpsFixed),
    HABITS("Habits", Icons.Default.Repeat),
    FINANCE("Finance", Icons.Default.AccountBalanceWallet),
    INSIGHTS("Insights", Icons.Default.BarChart)
}
// Incremental test
// Incremental test 2
// Incremental test 3
// Incremental test 4
