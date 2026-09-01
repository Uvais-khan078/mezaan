package com.example.meezan.data.scheduler
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.net.toUri
import android.provider.Settings
import com.example.meezan.MeezanApplication
import kotlinx.coroutines.flow.first
import com.example.meezan.R
import com.example.meezan.data.entities.PrayerReminderRule
import com.example.meezan.data.entities.PrayerTiming
import com.example.meezan.di.AppContainer
import com.example.meezan.util.AppError
import com.example.meezan.util.AppErrorException
import com.example.meezan.util.Constants
import kotlinx.coroutines.*
/**
 * Manages scheduling of alarms and reminders using AlarmManager
 * Handles both exact and inexact alarms based on device capabilities
 */
class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    /**
     * Schedule prayer reminders based on rules and current prayer timings
     * This must be called after fetching new prayer timings to reschedule alarms
     */
    fun schedulePrayerReminder(
        rule: PrayerReminderRule,
        prayerTiming: PrayerTiming
    ) {
        val baseTimeMillisSinceMidnight = when (rule.prayerName.uppercase()) {
            "FAJR" -> if (rule.anchor == "START") prayerTiming.fajrStart else prayerTiming.fajrEnd
            "SUNRISE" -> prayerTiming.sunrise
            "DHUHR" -> if (rule.anchor == "START") prayerTiming.dhuhrStart else prayerTiming.dhuhrEnd
            "ASR" -> if (rule.anchor == "START") prayerTiming.asrStart else prayerTiming.asrEnd
            "SUNSET" -> prayerTiming.sunset
            "MAGHRIB" -> if (rule.anchor == "START") prayerTiming.maghribStart else prayerTiming.maghribEnd
            "ISHA" -> if (rule.anchor == "START") prayerTiming.ishaStart else prayerTiming.ishaEnd
            else -> return
        }
        
        // Use Calendar for robust time calculation
        val calendar = java.util.Calendar.getInstance()
        // Set to the date of the prayer timing
        val prayerDateCal = java.util.Calendar.getInstance().apply { timeInMillis = prayerTiming.date }
        
        calendar.set(java.util.Calendar.YEAR, prayerDateCal.get(java.util.Calendar.YEAR))
        calendar.set(java.util.Calendar.MONTH, prayerDateCal.get(java.util.Calendar.MONTH))
        calendar.set(java.util.Calendar.DAY_OF_MONTH, prayerDateCal.get(java.util.Calendar.DAY_OF_MONTH))
        
        // Add the base prayer time and the offset
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        
        val totalOffsetMillis = baseTimeMillisSinceMidnight + (rule.offsetMinutes * Constants.Time.MILLIS_IN_MINUTE)
        calendar.timeInMillis += totalOffsetMillis

        // If calculated time is in the past, shift to tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        scheduleExactAlarm(
            calendar.timeInMillis,
            createPrayerReminderIntent(rule.id.toInt())
        )
    }
    /**
     * Schedule a general reminder (for tasks, habits, etc.)
     */
    fun scheduleReminder(
        reminderTime: Long,
        reminderId: Int,
        reminderType: ReminderType
    ) {
        if (reminderTime > System.currentTimeMillis()) {
            scheduleExactAlarm(
                reminderTime,
                createGenericReminderIntent(reminderId, reminderType)
            )
        }
    }
    /**
     * Cancel a previously scheduled reminder
     */
    fun cancelReminder(reminderId: Int, reminderType: ReminderType) {
        val intent = createGenericReminderIntent(reminderId, reminderType)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
    /**
     * Cancel a prayer reminder
     */
    fun cancelPrayerReminder(ruleId: Int) {
        val intent = createPrayerReminderIntent(ruleId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ruleId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Trigger a test notification immediately
     */
    fun triggerTestNotification(isAlarm: Boolean) {
        val alarmIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("EXTRA_TITLE", "Test Alarm")
            putExtra("EXTRA_CONTENT", "If you hear this, your looping alarm is working!")
            putExtra("EXTRA_ID", 9999)
            putExtra("EXTRA_CHANNEL", MeezanApplication.PRAYER_CHANNEL_ID)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(alarmIntent)
        } else {
            context.startService(alarmIntent)
        }
    }
    /**
     * Schedule an exact alarm using setAlarmClock
     * This is the most reliable method and shows an alarm icon in the status bar
     */
    private fun scheduleExactAlarm(
        triggerAtMillis: Long,
        intent: Intent
    ) {
        val requestCode = intent.getIntExtra("rule_id", intent.getIntExtra("reminder_id", intent.hashCode()))
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val info = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
        
        try {
            android.util.Log.d("ReminderScheduler", "Scheduling AlarmClock for: ${java.util.Date(triggerAtMillis)}")
            alarmManager.setAlarmClock(info, pendingIntent)
        } catch (e: SecurityException) {
            throw AppErrorException(AppError.ExactAlarmPermissionDenied)
        }
    }
    private fun createPrayerReminderIntent(ruleId: Int): Intent {
        return Intent(context, PrayerReminderReceiver::class.java).apply {
            action = "com.example.meezan.PRAYER_REMINDER"
            putExtra("rule_id", ruleId)
        }
    }
    private fun createGenericReminderIntent(
        reminderId: Int,
        reminderType: ReminderType
    ): Intent {
        return Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.meezan.GENERIC_REMINDER"
            putExtra("reminder_id", reminderId)
            putExtra("reminder_type", reminderType.name)
        }
    }
    enum class ReminderType {
        TASK, HABIT
    }
}
/**
 * Receives prayer reminder alarms and shows notifications
 */
class PrayerReminderReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val ruleId = intent.getIntExtra("rule_id", -1)
        
        android.util.Log.d("PrayerReceiver", "Received broadcast for ruleId: $ruleId")
        
        if (ruleId == -1) return

        val pendingResult = goAsync()
        val appContainer = (context.applicationContext as MeezanApplication).container
        
        // Use application-level scope for background processing
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val rule = appContainer.prayerReminderRuleDao.getRuleById(ruleId.toLong())
                if (rule == null) {
                    android.util.Log.e("PrayerReceiver", "Rule with id $ruleId not found in DB")
                    return@launch
                }
                
                android.util.Log.d("PrayerReceiver", "Starting AlarmService for: ${rule.prayerName}")
                
                // Start Looping Alarm Service for Prayer
                val alarmIntent = Intent(context, AlarmService::class.java).apply {
                    putExtra("EXTRA_TITLE", "Prayer Reminder")
                    val offsetStr = if (rule.offsetMinutes >= 0) "+${rule.offsetMinutes}" else "${rule.offsetMinutes}"
                    putExtra("EXTRA_CONTENT", "It's time for ${rule.prayerName} ($offsetStr mins from ${rule.anchor.lowercase()})")
                    putExtra("EXTRA_ID", ruleId)
                    putExtra("EXTRA_CHANNEL", MeezanApplication.PRAYER_CHANNEL_ID)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(alarmIntent)
                } else {
                    context.startService(alarmIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class ReminderReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val reminderId = intent.getIntExtra("reminder_id", -1)
        val typeStr = intent.getStringExtra("reminder_type") ?: return
        val type = try {
            ReminderScheduler.ReminderType.valueOf(typeStr)
        } catch (e: Exception) {
            return
        }

        val pendingResult = goAsync()
        val appContainer = (context.applicationContext as MeezanApplication).container

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val title: String
                val content: String
                val channelId: String

                when (type) {
                    ReminderScheduler.ReminderType.TASK -> {
                        val task = appContainer.goalTaskDao.getTaskById(reminderId.toLong()) ?: return@launch
                        title = "Task Reminder"
                        content = "Don't forget: ${task.taskName}"
                        channelId = MeezanApplication.TASK_CHANNEL_ID
                    }
                    ReminderScheduler.ReminderType.HABIT -> {
                        val habit = appContainer.habitDao.getHabitById(reminderId.toLong()) ?: return@launch
                        title = "Habit Reminder"
                        content = "Time for: ${habit.name}"
                        channelId = MeezanApplication.HABIT_CHANNEL_ID
                    }
                }

                // Start Looping Alarm Service for General Reminders
                val alarmIntent = Intent(context, AlarmService::class.java).apply {
                    putExtra("EXTRA_TITLE", title)
                    putExtra("EXTRA_CONTENT", content)
                    putExtra("EXTRA_ID", reminderId)
                    putExtra("EXTRA_CHANNEL", channelId)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(alarmIntent)
                } else {
                    context.startService(alarmIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private suspend fun showNotification(
    context: Context,
    title: String,
    content: String,
    channelId: String,
    notificationId: Int,
    isAlarm: Boolean = false
) = withContext(Dispatchers.IO) {
    val appContainer = (context.applicationContext as MeezanApplication).container
    
    val prefs = appContainer.userPreferencesRepository.userPreferencesFlow.first()
    val customSoundUri = prefs.notificationSoundUri?.toUri()

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(content)
        .setPriority(if (isAlarm) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)

    if (isAlarm) {
        builder.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI)
        builder.setFullScreenIntent(null, true) // Placeholder for real full screen alarm
    } else if (customSoundUri != null) {
        builder.setSound(customSoundUri)
    }

    with(NotificationManagerCompat.from(context)) {
        try {
            notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Handled via AppError propagation in VM if needed, but here we just log
            android.util.Log.e("ReminderScheduler", "SecurityException while showing notification", e)
        } catch (e: Exception) {
            android.util.Log.e("ReminderScheduler", "Unexpected exception while showing notification", e)
        }
    }
}
