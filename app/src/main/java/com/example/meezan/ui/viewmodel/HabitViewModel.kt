package com.example.meezan.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.meezan.data.entities.Habit
import com.example.meezan.data.entities.HabitLog
import com.example.meezan.data.entities.HabitWithHistory
import com.example.meezan.data.repository.HabitRepository
import com.example.meezan.data.repository.ProductivityScoreRepository
import com.example.meezan.util.AppError
import com.example.meezan.util.AppErrorException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class HabitViewModel(
    private val habitRepository: HabitRepository,
    private val scoreRepository: ProductivityScoreRepository,
) : BaseMeezanViewModel() {

    private val _uiState = MutableStateFlow(HabitUiState())
    val uiState: StateFlow<HabitUiState> = _uiState.asStateFlow()

    init {
        observeHabits()
        observeTodayLogs()
    }

    private fun observeHabits() {
        habitRepository.observeActiveHabits()
            .map { habits -> habitRepository.getHabitsWithHistory(habits) }
            .onEach { habitsWithHistory ->
                _uiState.update { it.copy(habits = habitsWithHistory) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeTodayLogs() {
        habitRepository.observeLogsForDate(System.currentTimeMillis())
            .onEach { logs ->
                _uiState.update { it.copy(todayLogs = logs) }
            }
            .launchIn(viewModelScope)
    }

    fun addHabit(name: String, icon: String = "") {
        viewModelScope.launch {
            habitRepository.addHabit(name, icon)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun toggleHabit(habitId: Long, completed: Boolean) {
        viewModelScope.launch {
            habitRepository.toggleHabit(habitId, System.currentTimeMillis(), completed)
                .onSuccess {
                    scoreRepository.calculateAndSaveDailyScore(System.currentTimeMillis())
                }
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            habitRepository.deleteHabit(habit)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun setHabitReminder(habitId: Long, hour: Int, minute: Int, enabled: Boolean) {
        viewModelScope.launch {
            habitRepository.setHabitReminder(habitId, hour, minute, enabled)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    companion object {
        fun provideFactory(
            habitRepository: HabitRepository,
            scoreRepository: ProductivityScoreRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HabitViewModel(habitRepository, scoreRepository)
            }
        }
    }
}

data class HabitUiState(
    val habits: List<HabitWithHistory> = emptyList(),
    val todayLogs: List<HabitLog> = emptyList()
)
