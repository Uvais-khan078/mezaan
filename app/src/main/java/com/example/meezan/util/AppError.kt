package com.example.meezan.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.meezan.R

/**
 * Sealed class representing all possible error categories in the app.
 */
sealed class AppError {
    object NoInternet : AppError()
    data class ApiError(val code: Int, val message: String) : AppError()
    object LocationPermissionDenied : AppError()
    object LocationDisabled : AppError()
    object NotificationPermissionDenied : AppError()
    object ExactAlarmPermissionDenied : AppError()
    data class DatabaseError(val throwable: Throwable) : AppError()
    data class RoadmapParseError(val reason: String) : AppError()
    data class ValidationError(val message: String) : AppError()
    data class InsufficientFunds(val available: Double) : AppError()
    data class UnknownError(val throwable: Throwable) : AppError()

    fun getActionIntent(context: Context): Intent? {
        return when (this) {
            is LocationPermissionDenied -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }
            is LocationDisabled -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            is NotificationPermissionDenied -> {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            }
            is ExactAlarmPermissionDenied -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                } else null
            }
            else -> null
        }
    }

    fun getMessageResId(): Int = when (this) {
        NoInternet -> R.string.error_no_internet
        is ApiError -> R.string.error_api_failure
        LocationPermissionDenied -> R.string.error_location_permission_denied
        LocationDisabled -> R.string.error_location_disabled
        NotificationPermissionDenied -> R.string.error_notification_permission_denied
        ExactAlarmPermissionDenied -> R.string.error_exact_alarm_permission_denied
        is DatabaseError -> R.string.error_database_failure
        is InsufficientFunds -> R.string.error_insufficient_funds
        is RoadmapParseError -> R.string.error_roadmap_parse_failure
        is ValidationError -> 0 // Handled separately as it uses raw message
        is UnknownError -> R.string.error_unknown
    }
}

class AppErrorException(val error: AppError) : Exception()
