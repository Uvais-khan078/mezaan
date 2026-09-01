package com.example.meezan.data.scheduler

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.meezan.MeezanApplication
import com.example.meezan.MainActivity
import com.example.meezan.data.entities.FocusSession
import com.example.meezan.data.entities.TaskStatus
import com.example.meezan.data.repository.FocusRepository
import com.example.meezan.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FocusTimerService : Service() {

    private val binder = TimerBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    private var timerJob: Job? = null
    
    private val _elapsedTimeSeconds = MutableStateFlow(0L)
    val elapsedTimeSeconds: StateFlow<Long> = _elapsedTimeSeconds.asStateFlow()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var currentTaskId: Long = -1
    private var currentTaskName: String = ""
    private var goalSeconds: Long = 0

    override fun onBind(intent: Intent?): IBinder = binder

    inner class TimerBinder : Binder() {
        fun getService(): FocusTimerService = this@FocusTimerService
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
                val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: ""
                val goalMins = intent.getIntExtra(EXTRA_GOAL_MINS, 0)
                goalSeconds = goalMins * 60L
                startTimer(taskId, taskName)
            }
            ACTION_STOP -> stopTimer()
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
        }
        return START_NOT_STICKY
    }

    private fun startTimer(taskId: Long, taskName: String) {
        if (_isRunning.value && currentTaskId == taskId) return
        
        currentTaskId = taskId
        currentTaskName = taskName
        _elapsedTimeSeconds.value = 0
        
        startForeground(NOTIFICATION_ID, createNotification(0))
        resumeTimer()
    }

    private fun pauseTimer() {
        _isRunning.value = false
        timerJob?.cancel()
        updateNotification()
    }

    private fun resumeTimer() {
        if (_isRunning.value) return
        _isRunning.value = true
        
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                _elapsedTimeSeconds.value += 1
                if (goalSeconds > 0 && _elapsedTimeSeconds.value == goalSeconds) {
                    playCompletionSound()
                }
                updateNotification()
            }
        }
    }

    private fun playCompletionSound() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val completionNotification = NotificationCompat.Builder(this, MeezanApplication.TASK_CHANNEL_ID)
            .setContentTitle("Focus Goal Reached!")
            .setContentText("You've completed your goal for: $currentTaskName")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID + 1, completionNotification)
    }

    private fun stopTimer() {
        val elapsedSeconds = _elapsedTimeSeconds.value
        val elapsedMinutes = (elapsedSeconds / 60).toInt()
        
        _isRunning.value = false
        timerJob?.cancel()
        
        if (elapsedMinutes > 0 && currentTaskId != -1L) {
            serviceScope.launch(Dispatchers.IO) {
                val appContainer = (applicationContext as MeezanApplication).container
                val focusRepo = appContainer.focusRepository
                
                focusRepo.insertSession(
                    FocusSession(
                        taskId = currentTaskId,
                        startTime = System.currentTimeMillis() - (elapsedSeconds * 1000),
                        endTime = System.currentTimeMillis(),
                        actualDurationMinutes = elapsedMinutes
                    )
                )
                
                // Update task status
                focusRepo.getTaskById(currentTaskId).onSuccess { task ->
                    if (task != null) {
                        focusRepo.getTotalFocusDuration(currentTaskId).onSuccess { totalFocus ->
                            val newStatus = if (totalFocus >= task.requiredDurationMinutes) TaskStatus.COMPLETED else TaskStatus.IN_PROGRESS
                            focusRepo.updateTask(task.copy(status = newStatus))
                        }
                    }
                }
            }
            if (goalSeconds > 0 && elapsedSeconds >= goalSeconds) {
                playCompletionSound()
            }
        }
        
        stopForeground(true)
        stopSelf()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(_elapsedTimeSeconds.value))
    }

    private fun createNotification(seconds: Long): Notification {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", h, m, s)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val pauseResumeAction = if (_isRunning.value) {
            val pauseIntent = Intent(this, FocusTimerService::class.java).apply { action = ACTION_PAUSE }
            val pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent).build()
        } else {
            val resumeIntent = Intent(this, FocusTimerService::class.java).apply { action = ACTION_RESUME }
            val resumePendingIntent = PendingIntent.getService(this, 2, resumeIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(android.R.drawable.ic_media_play, "Resume", resumePendingIntent).build()
        }

        val stopIntent = Intent(this, FocusTimerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val stopAction = NotificationCompat.Action.Builder(android.R.drawable.ic_delete, "Stop", stopPendingIntent).build()

        return NotificationCompat.Builder(this, MeezanApplication.TASK_CHANNEL_ID)
            .setContentTitle("Focusing on: $currentTaskName")
            .setContentText("Elapsed time: $timeStr")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle())
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
        const val EXTRA_TASK_NAME = "EXTRA_TASK_NAME"
        const val EXTRA_GOAL_MINS = "EXTRA_GOAL_MINS"
        
        private const val NOTIFICATION_ID = 1001
    }
}
