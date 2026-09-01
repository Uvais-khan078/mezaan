package com.example.meezan.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.meezan.data.entities.Goal
import com.example.meezan.data.entities.GoalTask
import com.example.meezan.data.entities.TaskStatus
import com.example.meezan.data.repository.GoalRepository
import com.example.meezan.domain.parser.ParsedRoadmap
import com.example.meezan.domain.parser.RoadmapParseException
import com.example.meezan.domain.parser.RoadmapParser
import com.example.meezan.util.AppError
import com.example.meezan.util.AppErrorException
import com.example.meezan.util.DateTimeUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GoalPlannerViewModel(
    private val goalRepository: GoalRepository,
) : BaseMeezanViewModel() {

    private val _uiState = MutableStateFlow(GoalPlannerUiState())
    val uiState: StateFlow<GoalPlannerUiState> = _uiState.asStateFlow()

    private val _currentDate = MutableStateFlow(DateTimeUtil.getTodayAtMidnight())
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val todayTasks: StateFlow<List<GoalTask>> = _currentDate.flatMapLatest { date ->
        goalRepository.observeTasksForDate(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _upcomingTask = MutableStateFlow<GoalTask?>(null)
    val upcomingTask: StateFlow<GoalTask?> = _upcomingTask.asStateFlow()

    init {
        observeDateChanges()
        observeGoals()
        fetchUpcomingTask()
    }

    private fun observeDateChanges() {
        DateTimeUtil.observeCurrentDate()
            .onEach { _currentDate.value = it }
            .launchIn(viewModelScope)
    }

    private fun fetchUpcomingTask() {
        viewModelScope.launch {
            goalRepository.getNextUpcomingTask(System.currentTimeMillis())
                .onSuccess { _upcomingTask.value = it }
        }
    }

    private fun observeGoals() {
        viewModelScope.launch {
            goalRepository.observeActiveGoals().collectLatest { goals ->
                _uiState.value = _uiState.value.copy(activeGoals = goals)
            }
        }
    }

    fun onInputChange(value: String) {
        _uiState.value = _uiState.value.copy(inputText = value, error = null, success = null)
    }

    fun clearGoalError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun parseRoadmap() {
        val text = _uiState.value.inputText
        val parsed = try {
            RoadmapParser.parse(text)
        } catch (e: RoadmapParseException) {
            _uiState.value = _uiState.value.copy(error = AppError.RoadmapParseError(reason = e.message ?: "Unknown"), parsedRoadmap = null)
            return
        }

        _uiState.value = _uiState.value.copy(parsedRoadmap = parsed, error = null, showSaveConfirmation = true)
    }

    fun dismissSaveConfirmation() {
        _uiState.value = _uiState.value.copy(showSaveConfirmation = false)
    }

    fun confirmSaveParsedRoadmap() {
        val parsed = _uiState.value.parsedRoadmap ?: return
        _uiState.value = _uiState.value.copy(showSaveConfirmation = false)
        viewModelScope.launch {
            goalRepository.saveParsedRoadmap(parsed)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        success = "Roadmap saved successfully",
                        error = null,
                        showSaveConfirmation = false
                    )
                }
                .onFailure { throwable ->
                    val error = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                    _uiState.value = _uiState.value.copy(
                        error = error,
                        success = null,
                        showSaveConfirmation = false
                    )
                }
        }
    }

    fun deleteGoal(goalId: Long) {
        viewModelScope.launch {
            goalRepository.deleteGoal(goalId)
                .onSuccess { _uiState.value = _uiState.value.copy(success = "Goal deleted") }
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun updateTaskStatus(task: GoalTask, status: TaskStatus) {
        viewModelScope.launch {
            goalRepository.updateTaskStatus(task, status)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun setTaskReminder(task: GoalTask, hour: Int, minute: Int) {
        viewModelScope.launch {
            goalRepository.setTaskReminder(task, hour, minute)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    companion object {
        fun provideFactory(goalRepository: GoalRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GoalPlannerViewModel(goalRepository)
            }
        }
    }
}

data class GoalPlannerUiState(
    val inputText: String = "",
    val parsedRoadmap: ParsedRoadmap? = null,
    val activeGoals: List<Goal> = emptyList(),
    val error: AppError? = null,
    val success: String? = null,
    val showSaveConfirmation: Boolean = false
)

