package com.example.meezan.util

object Constants {
    const val DEFAULT_CALCULATION_METHOD = 2 // ISNA
    const val DEFAULT_MADHAB = 1 // Hanafi
    const val PRAYER_COUNT = 5
    
    object Time {
        const val MILLIS_IN_SECOND = 1000L
        const val SECONDS_IN_MINUTE = 60L
        const val MINUTES_IN_HOUR = 60L
        const val HOURS_IN_DAY = 24L
        
        const val MILLIS_IN_MINUTE = MILLIS_IN_SECOND * SECONDS_IN_MINUTE
        const val MILLIS_IN_HOUR = MINUTES_IN_HOUR * MILLIS_IN_MINUTE
        const val MILLIS_IN_DAY = HOURS_IN_DAY * MILLIS_IN_HOUR
    }
    
    object Reminders {
        const val DEFAULT_TASK_REMINDER_HOUR = 19
        const val DEFAULT_TASK_REMINDER_MINUTE = 0
    }
    
    object Analytics {
        const val HEATMAP_WEEKS = 5
        const val DAYS_IN_WEEK = 7
        const val HEATMAP_TOTAL_DAYS = HEATMAP_WEEKS * DAYS_IN_WEEK
        const val WEEK_DAYS = 7
        const val MONTH_DAYS = 30
    }
}
