package com.example.meezan

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.meezan.di.AppContainer
import com.example.meezan.data.scheduler.DailyRefreshWorker
import kotlinx.coroutines.*

/**
 * Meezan Application entry point.
 */
class MeezanApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: Exception) {
            android.util.Log.e("MeezanApp", "Failed to load sqlcipher", e)
        }
        com.example.meezan.util.MeezanCrashHandler.initialize(this)
        container = AppContainer(this)
        createNotificationChannels()
        
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.habitRepository.seedPrayerHabits()
        }
        
        // Optimized Startup: Defer background refresh with a stable scope
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            delay(3000)
            DailyRefreshWorker.schedule(this@MeezanApplication)
        }
    }

    private fun createNotificationChannels() {
        val prayerChannel = NotificationChannel(
            PRAYER_CHANNEL_ID,
            "Prayer Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for prayer times"
        }

        val taskChannel = NotificationChannel(
            TASK_CHANNEL_ID,
            "Task Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for goal tasks"
        }

        val habitChannel = NotificationChannel(
            HABIT_CHANNEL_ID,
            "Habit Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for daily habits"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.let {
            it.createNotificationChannel(prayerChannel)
            it.createNotificationChannel(taskChannel)
            it.createNotificationChannel(habitChannel)
        }
    }

    companion object {
        const val PRAYER_CHANNEL_ID = "prayer_reminders"
        const val TASK_CHANNEL_ID = "task_reminders"
        const val HABIT_CHANNEL_ID = "habit_reminders"
    }
}

