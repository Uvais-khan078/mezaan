package com.example.meezan.data.scheduler

import android.content.Context
import androidx.work.*
import com.example.meezan.MeezanApplication
import com.example.meezan.data.repository.PrayerTimingRepository
import com.example.meezan.data.repository.UserPreferencesRepository
import com.example.meezan.data.repository.ProductivityScoreRepository
import com.example.meezan.util.AppError
import com.example.meezan.util.AppErrorException
import com.example.meezan.util.Constants
import com.example.meezan.util.DateTimeUtil
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContainer = (applicationContext as MeezanApplication).container
        val prefsRepo = appContainer.userPreferencesRepository
        val timingRepo = appContainer.prayerTimingRepository
        val scoreRepo = appContainer.productivityScoreRepository
        val reminderScheduler = appContainer.reminderScheduler
        val ruleDao = appContainer.prayerReminderRuleDao
        val taskDao = appContainer.goalTaskDao
        val habitDao = appContainer.habitDao
        val financeRepo = appContainer.financeRepository

        var hasCriticalFailure = false

        // 1. Recalculate yesterday's score
        runStep("Recalculate Score") {
            val yesterday = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
            }.timeInMillis
            scoreRepo.calculateAndSaveDailyScore(DateTimeUtil.normalizeToMidnight(yesterday))
        }

        // 2. Initialize today's budget / Recalculate Daily Limit
        runStep("Recalculate Daily Limit") {
            val profile = appContainer.financeProfileDao.getProfile()
            if (profile != null) {
                financeRepo.recalculateDailyLimit(profile)
            }
        }

        // 3. Fetch today's prayer timings
        runStep("Prayer Timings & Reminders") {
            val prefs = prefsRepo.userPreferencesFlow.first()
            if (prefs.latitude != 0.0 && prefs.longitude != 0.0) {
                val fetchResult = timingRepo.fetchAndSavePrayerTimings(
                    prefs.latitude,
                    longitude = prefs.longitude,
                    method = prefs.calculationMethod,
                    school = prefs.madhab
                )
                
                fetchResult.onSuccess { timing ->
                    val rules = ruleDao.getEnabledRules()
                    for (rule in rules) {
                        try {
                            reminderScheduler.schedulePrayerReminder(rule, timing)
                        } catch (e: Exception) {
                            android.util.Log.e("DailyRefreshWorker", "Failed to schedule rule ${rule.id}", e)
                        }
                    }
                }.onFailure {
                    android.util.Log.e("DailyRefreshWorker", "Prayer timing fetch failed")
                    hasCriticalFailure = true // Mark for retry
                }
            }
        }

        // 4. Reschedule Task Reminders for today
        runStep("Reschedule Tasks") {
            val today = DateTimeUtil.getTodayAtMidnight()
            val tasks = taskDao.getTasksForDate(today)
            tasks.forEach { task ->
                task.reminderTime?.let { time ->
                    try {
                        reminderScheduler.scheduleReminder(
                            reminderTime = time,
                            reminderId = task.id.toInt(),
                            reminderType = ReminderScheduler.ReminderType.TASK
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("DailyRefreshWorker", "Failed to schedule task ${task.id}", e)
                    }
                }
            }
        }

        // 5. Reschedule Habit Reminders
        runStep("Reschedule Habits") {
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
                        android.util.Log.e("DailyRefreshWorker", "Failed to schedule habit ${habit.id}", e)
                    }
                }
            }
        }

        return when {
            hasCriticalFailure && runAttemptCount < 3 -> Result.retry()
            hasCriticalFailure -> Result.failure()
            else -> Result.success()
        }
    }

    private suspend fun runStep(name: String, step: suspend () -> Unit) {
        try {
            step()
        } catch (e: Exception) {
            android.util.Log.e("DailyRefreshWorker", "Step '$name' failed", e)
        }
    }

    companion object {
        private const val WORK_NAME = "daily_refresh_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<DailyRefreshWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(calculateDelayToMidnight(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        private fun calculateDelayToMidnight(): Long {
            val now = Calendar.getInstance()
            val midnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return midnight.timeInMillis - now.timeInMillis
        }
    }
}
