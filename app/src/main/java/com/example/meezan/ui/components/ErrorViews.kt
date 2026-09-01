package com.example.meezan.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.meezan.R
import com.example.meezan.util.AppError

/**
 * Composable that displays a user-friendly error dialog based on [AppError].
 */
@Composable
fun MeezanErrorDialog(
    error: AppError,
    onDismiss: () -> Unit,
    onAction: () -> Unit
) {
    val context = LocalContext.current
    val title = when (error) {
        AppError.NoInternet -> "No Internet"
        is AppError.ApiError -> "Service Error"
        AppError.LocationPermissionDenied -> "Permission Required"
        AppError.LocationDisabled -> "Location Disabled"
        AppError.NotificationPermissionDenied -> "Reminders Disabled"
        AppError.ExactAlarmPermissionDenied -> "Alarms Disabled"
        is AppError.InsufficientFunds -> "Insufficient Funds"
        is AppError.DatabaseError -> "Database Error"
        is AppError.RoadmapParseError -> "Parsing Failed"
        is AppError.ValidationError -> "Invalid Input"
        is AppError.UnknownError -> "Oops!"
    }

    val actionText = when (error) {
        AppError.NoInternet, 
        is AppError.ApiError,
        is AppError.DatabaseError,
        is AppError.UnknownError -> androidx.compose.ui.res.stringResource(R.string.action_retry)
        AppError.LocationPermissionDenied -> androidx.compose.ui.res.stringResource(R.string.action_grant_permission)
        AppError.LocationDisabled -> androidx.compose.ui.res.stringResource(R.string.action_turn_on_location)
        AppError.NotificationPermissionDenied -> androidx.compose.ui.res.stringResource(R.string.action_enable_notifications)
        AppError.ExactAlarmPermissionDenied -> androidx.compose.ui.res.stringResource(R.string.action_allow_exact_alarms)
        is AppError.RoadmapParseError -> androidx.compose.ui.res.stringResource(R.string.action_edit_roadmap)
        else -> androidx.compose.ui.res.stringResource(R.string.action_ok)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(error.userFriendlyMessage()) },
        confirmButton = {
            Button(onClick = {
                val intent = error.getActionIntent(context)
                if (intent != null) {
                    context.startActivity(intent)
                } else {
                    onAction()
                }
                onDismiss()
            }) {
                Text(actionText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(R.string.action_dismiss))
            }
        }
    )
}

/**
 * Extension function for AppError to get user friendly message.
 */
@Composable
fun AppError.userFriendlyMessage(): String {
    val message = when (this) {
        AppError.NoInternet -> androidx.compose.ui.res.stringResource(R.string.error_no_internet)
        is AppError.ApiError -> androidx.compose.ui.res.stringResource(R.string.error_api_failure, code, message)
        AppError.LocationPermissionDenied -> androidx.compose.ui.res.stringResource(R.string.error_location_permission_denied)
        AppError.LocationDisabled -> androidx.compose.ui.res.stringResource(R.string.error_location_disabled)
        AppError.NotificationPermissionDenied -> androidx.compose.ui.res.stringResource(R.string.error_notification_permission_denied)
        AppError.ExactAlarmPermissionDenied -> androidx.compose.ui.res.stringResource(R.string.error_exact_alarm_permission_denied)
        is AppError.InsufficientFunds -> androidx.compose.ui.res.stringResource(R.string.error_insufficient_funds, available)
        is AppError.DatabaseError -> androidx.compose.ui.res.stringResource(R.string.error_database_failure)
        is AppError.RoadmapParseError -> androidx.compose.ui.res.stringResource(R.string.error_roadmap_parse_failure, reason)
        is AppError.ValidationError -> message
        is AppError.UnknownError -> androidx.compose.ui.res.stringResource(R.string.error_unknown)
    }
    
    return if (this is AppError.NoInternet) {
        "$message\n\n${androidx.compose.ui.res.stringResource(R.string.note_showing_cached_data)}"
    } else message
}

/**
 * Effect to handle non-blocking snackbar errors.
 */
@Composable
fun MeezanSnackbarErrorEffect(
    snackbarError: AppError?,
    snackbarHostState: SnackbarHostState,
    onClearError: () -> Unit
) {
    if (snackbarError != null) {
        val message = snackbarError.userFriendlyMessage()
        LaunchedEffect(snackbarError) {
            snackbarHostState.showSnackbar(message)
            onClearError()
        }
    }
}

/**
 * A simpler error card for inline errors (e.g. in Insights).
 */
@Composable
fun MeezanErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Try Again")
                }
            }
        }
    }
}
