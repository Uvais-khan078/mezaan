package com.example.meezan.data.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.meezan.MeezanApplication
import com.example.meezan.util.Constants
import com.example.meezan.util.DateTimeUtil
import kotlinx.coroutines.flow.first
import java.util.Calendar

class RescheduleAlarmsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContainer = (applicationContext as MeezanApplication).container
        val prayerTimingRepository = appContainer.prayerTimingRepository
        val reminderScheduler = appContainer.reminderScheduler
        val prayerReminderRuleDao = appContainer.prayerReminderRuleDao
        val goalTaskDao = appContainer.goalTaskDao
        val habitDao = appContainer.habitDao

        // 1. Reschedule Prayer Reminders
        runStep("Prayer Reminders") {
            val latestTiming = prayerTimingRepository.getLatestPrayerTiming().getOrNull()
            if (latestTiming != null) {
                val rules = prayerReminderRuleDao.getEnabledRules()
                for (rule in rules) {
                    try {
                        reminderScheduler.schedulePrayerReminder(rule, latestTiming)
                    } catch (e: Exception) {
                        android.util.Log.e("RescheduleAlarmsWorker", "Failed to schedule rule ${rule.id}", e)
                    }
                }
            }
        }

        // 2. Reschedule Task Reminders for today
        runStep("Task Reminders") {
            val today = DateTimeUtil.getTodayAtMidnight()
            val tasks = goalTaskDao.getTasksForDate(today)
            tasks.forEach { task ->
                task.reminderTime?.let { time ->
                    try {
                        reminderScheduler.scheduleReminder(
                            reminderTime = time,
                            reminderId = task.id.toInt(),
                            reminderType = ReminderScheduler.ReminderType.TASK
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("RescheduleAlarmsWorker", "Failed to schedule task ${task.id}", e)
                    }
                }
            }
        }

        runStep("Habit Reminders") {
            val habits = habitDao.observeActiveHabits().first()
            habits.filter { it.isReminderEnabled }.forEach { habit ->
                habit.reminderTime?.let { millisSinceMidnight ->
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, (millisSinceMidnight / Constants.Time.MILLIS_IN_HOUR).toInt())
                        set(Calendar.MINUTE, ((millisSinceMidnight % Constants.Time.MILLIS_IN_HOUR) / Constants.Time.MILLIS_IN_MINUTE).toInt())
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        
                        if (timeInMillis <= System.currentTimeMillis()) {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }
                    
                    try {
                        reminderScheduler.scheduleReminder(
                            reminderTime = cal.timeInMillis,
                            reminderId = habit.id.toInt(),
                            reminderType = ReminderScheduler.ReminderType.HABIT
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("RescheduleAlarmsWorker", "Failed to schedule habit ${habit.id}", e)
                    }
                }
            }
        }
        return Result.success()
    }

    private suspend fun runStep(name: String, step: suspend () -> Unit) {
        try {
            step()
        } catch (e: Exception) {
            android.util.Log.e("RescheduleAlarmsWorker", "Step '$name' failed", e)
        }
    }
}
