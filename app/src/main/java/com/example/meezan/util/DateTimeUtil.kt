package com.example.meezan.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

import kotlin.time.Duration.Companion.seconds

/**
 * Utility functions for date and time formatting
 */
object DateTimeUtil {

    /**
     * Convert milliseconds since midnight to 12-hour format (e.g., "5:30 PM")
     */
    fun formatTimeFromMillis(millisSinceMidnight: Long): String {
        val totalMinutes = millisSinceMidnight / Constants.Time.MILLIS_IN_MINUTE
        val hours = (totalMinutes / Constants.Time.MINUTES_IN_HOUR).toInt()
        val minutes = (totalMinutes % Constants.Time.MINUTES_IN_HOUR).toInt()
        
        val amPm = if (hours >= 12) "PM" else "AM"
        val hour12 = when {
            hours == 0 -> 12
            hours > 12 -> hours - 12
            else -> hours
        }
        
        return String.format(Locale.getDefault(), "%02d:%02d %s", hour12, minutes, amPm)
    }

    /**
     * Convert milliseconds duration to readable format (e.g., "2h 30m", "45m")
     */
    fun formatDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis / Constants.Time.MILLIS_IN_SECOND
        val hours = totalSeconds / (Constants.Time.SECONDS_IN_MINUTE * Constants.Time.MINUTES_IN_HOUR)
        val minutes = (totalSeconds % (Constants.Time.SECONDS_IN_MINUTE * Constants.Time.MINUTES_IN_HOUR)) / Constants.Time.SECONDS_IN_MINUTE

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "0m"
        }
    }

    /**
     * Format countdown time (e.g., "2h 30m 45s")
     */
    fun formatCountdown(durationMillis: Long): String {
        val totalSeconds = durationMillis / Constants.Time.MILLIS_IN_SECOND
        val totalMinutes = totalSeconds / Constants.Time.SECONDS_IN_MINUTE
        val hours = totalMinutes / Constants.Time.MINUTES_IN_HOUR
        val minutes = totalMinutes % Constants.Time.MINUTES_IN_HOUR
        val seconds = totalSeconds % Constants.Time.SECONDS_IN_MINUTE

        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Get today's date at midnight in milliseconds
     */
    fun getTodayAtMidnight(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Check if a date (epoch millis) is today
     */
    fun isToday(dateMillis: Long): Boolean {
        return normalizeToMidnight(dateMillis) == getTodayAtMidnight()
    }

    /**
     * Normalize date to start of day (00:00:00) in milliseconds
     */
    fun normalizeToMidnight(timeMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Observes the current date, emitting the new midnight timestamp whenever the day changes.
     */
    fun observeCurrentDate(): kotlinx.coroutines.flow.Flow<Long> = kotlinx.coroutines.flow.flow {
        var lastEmitted = getTodayAtMidnight()
        emit(lastEmitted)
        while (true) {
            kotlinx.coroutines.delay(30.seconds) // Check every 30 seconds
            val current = getTodayAtMidnight()
            if (current != lastEmitted) {
                lastEmitted = current
                emit(lastEmitted)
            }
        }
    }
}

