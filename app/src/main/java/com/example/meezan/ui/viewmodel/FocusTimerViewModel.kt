package com.example.meezan.ui.viewmodel

import android.content.*
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.meezan.data.entities.GoalTask
import com.example.meezan.data.repository.FocusRepository
import com.example.meezan.data.scheduler.FocusTimerService
import com.example.meezan.util.AppError
import com.example.meezan.util.AppErrorException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FocusTimerViewModel(
    private val applicationContext: Context,
    private val focusRepository: FocusRepository,
    private val taskId: Long
) : BaseMeezanViewModel() {

    private val _task = MutableStateFlow<GoalTask?>(null)
    val task: StateFlow<GoalTask?> = _task.asStateFlow()

    private val _timerState = MutableStateFlow(FocusTimerUiState())
    val timerState: StateFlow<FocusTimerUiState> = _timerState.asStateFlow()

    private var timerService: FocusTimerService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as FocusTimerService.TimerBinder
            timerService = binder.getService()
            isBound = true
            observeService()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            timerService = null
        }
    }

    init {
        loadTask()
        bindService()
    }

    private fun loadTask() {
        viewModelScope.launch {
            focusRepository.getTaskById(taskId)
                .onSuccess { _task.value = it }
                .onFailure { throwable ->
                    _error.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    private fun bindService() {
        val intent = Intent(applicationContext, FocusTimerService::class.java)
        applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun observeService() {
        timerService?.let { service ->
            service.elapsedTimeSeconds
                .onEach { seconds -> _timerState.update { it.copy(elapsedSeconds = seconds) } }
                .launchIn(viewModelScope)
            
            service.isRunning
                .onEach { running -> _timerState.update { it.copy(isRunning = running) } }
                .launchIn(viewModelScope)
        }
    }

    fun checkPreConditions(): AppError? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (androidx.core.content.ContextCompat.checkSelfPermission(applicationContext, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return AppError.NotificationPermissionDenied
            }
        }

        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(applicationContext.packageName)) {
            return AppError.ValidationError("Battery optimization is active. The timer may be killed in the background. Please disable optimization for Meezan.")
        }
        return null
    }

    fun startTimer() {
        val preError = checkPreConditions()
        if (preError != null && preError is AppError.NotificationPermissionDenied) {
            _error.value = preError
            return
        }
        
        if (preError != null && preError is AppError.ValidationError) {
            _snackbarError.value = preError
        }

        try {
            val intent = Intent(applicationContext, FocusTimerService::class.java).apply {
                action = FocusTimerService.ACTION_START
                putExtra(FocusTimerService.EXTRA_TASK_ID, taskId)
                putExtra(FocusTimerService.EXTRA_TASK_NAME, _task.value?.taskName ?: "Task")
                putExtra(FocusTimerService.EXTRA_GOAL_MINS, _task.value?.requiredDurationMinutes ?: 0)
            }
            applicationContext.startForegroundService(intent)
        } catch (e: Exception) {
            _error.value = AppError.UnknownError(e)
        }
    }

    fun pauseResumeTimer() {
        val action = if (_timerState.value.isRunning) FocusTimerService.ACTION_PAUSE else FocusTimerService.ACTION_RESUME
        val intent = Intent(applicationContext, FocusTimerService::class.java).apply {
            this.action = action
        }
        applicationContext.startService(intent)
    }

    fun stopTimer() {
        val intent = Intent(applicationContext, FocusTimerService::class.java).apply {
            action = FocusTimerService.ACTION_STOP
        }
        applicationContext.startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            applicationContext.unbindService(connection)
            isBound = false
            timerService = null
        }
    }

    companion object {
        fun provideFactory(
            applicationContext: Context,
            focusRepository: FocusRepository,
            taskId: Long
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FocusTimerViewModel(applicationContext, focusRepository, taskId)
            }
        }
    }
}

data class FocusTimerUiState(
    val elapsedSeconds: Long = 0,
    val isRunning: Boolean = false
)
