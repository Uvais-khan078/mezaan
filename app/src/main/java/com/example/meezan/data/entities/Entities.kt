
package com.example.meezan.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/**
 * Entity for storing prayer timings for each day
 */
@Entity(tableName = "prayer_timings")
data class PrayerTiming(
    @PrimaryKey(autoGenerate = false)
    val date: Long, // Date stored as epoch milliseconds
    val latitude: Double,
    val longitude: Double,
    val method: String, // e.g., "ISNA", "Karachi", "MWL"
    val fajrStart: Long, // Time stored as milliseconds since midnight
    val fajrEnd: Long,
    val sunrise: Long,
    val dhuhrStart: Long,
    val dhuhrEnd: Long,
    val asrStart: Long,
    val asrEnd: Long,
    val sunset: Long,
    val maghribStart: Long,
    val maghribEnd: Long,
    val ishaStart: Long,
    val ishaEnd: Long
)

/**
 * Entity for logging prayer completion
 */
@Entity(
    tableName = "prayer_logs",
    indices = [androidx.room.Index(value = ["date", "prayerName"], unique = true)]
)
data class PrayerLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long, // Epoch milliseconds for the date
    val prayerName: String, // e.g., "Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"
    val completed: Boolean = false
)

/**
 * Entity for storing dynamic prayer reminder rules
 */
@Entity(tableName = "prayer_reminder_rules")
data class PrayerReminderRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val prayerName: String, // e.g., "Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"
    val anchor: String, // "START" or "END"
    val offsetMinutes: Int, // Positive for after, negative for before
    val enabled: Boolean = true,
    val isAlarm: Boolean = false
)

/**
 * Entity for storing goals
 */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdDate: Long, // Epoch milliseconds
    val isActive: Boolean = true
)

/**
 * Task Status enum
 */
enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
    NOT_COMPLETED
}

/**
 * Entity for storing daily tasks for goals
 */
@Entity(
    tableName = "goal_tasks",
    indices = [
        androidx.room.Index(value = ["taskDate"]),
        androidx.room.Index(value = ["goalId"])
    ]
)
data class GoalTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val goalId: Long,
    val dayNumber: Int,
    val taskName: String,
    val requiredDurationMinutes: Int,
    val taskDate: Long, // Epoch milliseconds for the date this task is scheduled
    val reminderTime: Long?, // Epoch milliseconds for the reminder time (optional)
    val status: TaskStatus = TaskStatus.PENDING
)

/**
 * Entity for storing focus sessions
 */
@Entity(
    tableName = "focus_sessions",
    indices = [androidx.room.Index(value = ["taskId"]), androidx.room.Index(value = ["startTime"])]
)
data class FocusSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val startTime: Long, // Epoch milliseconds
    val endTime: Long?, // Epoch milliseconds (null if still in progress)
    val actualDurationMinutes: Int = 0
)

/**
 * Entity for storing habits
 */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String = "", // Can store emoji or icon name
    val createdDate: Long, // Epoch milliseconds
    val isActive: Boolean = true,
    val reminderTime: Long? = null, // Milliseconds since midnight
    val isReminderEnabled: Boolean = false,
    val isDeletable: Boolean = true
)

/**
 * Entity for storing daily habit logs
 */
@Entity(
    tableName = "habit_logs",
    indices = [androidx.room.Index(value = ["habitId"]), androidx.room.Index(value = ["date"])]
)
data class HabitLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val habitId: Long,
    val date: Long, // Epoch milliseconds for the date
    val completed: Boolean = false
)

/**
 * Entity for storing daily productivity score
 */
@Entity(tableName = "daily_scores")
data class DailyScore(
    @PrimaryKey(autoGenerate = false)
    val date: Long, // Epoch milliseconds
    val prayerScore: Float = 0f, // 0-100
    val goalCompletionScore: Float = 0f, // 0-100
    val focusScore: Float = 0f, // 0-100
    val habitScore: Float = 0f, // 0-100
    val overallScore: Float = 0f // Simple average of the above
)

