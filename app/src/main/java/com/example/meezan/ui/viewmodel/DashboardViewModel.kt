package com.example.meezan.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.meezan.data.entities.DailyScore
import com.example.meezan.data.entities.FinanceProfile
import com.example.meezan.data.repository.FinanceRepository
import com.example.meezan.data.repository.ProductivityScoreRepository
import com.example.meezan.util.DateTimeUtil
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val scoreRepository: ProductivityScoreRepository,
    private val financeRepository: FinanceRepository
) : ViewModel() {

    private val _dailyScore = MutableStateFlow<DailyScore?>(null)
    val dailyScore: StateFlow<DailyScore?> = _dailyScore.asStateFlow()

    private val _financeProfile = MutableStateFlow<FinanceProfile?>(null)
    val financeProfile: StateFlow<FinanceProfile?> = _financeProfile.asStateFlow()

    private val _todaysSpent = MutableStateFlow(0.0)
    val todaysSpent: StateFlow<Double> = _todaysSpent.asStateFlow()

    init {
        observeDailyScore()
        observeFinance()
    }

    private fun observeDailyScore() {
        scoreRepository.observeScoreForDate(System.currentTimeMillis())
            .onEach { score -> _dailyScore.value = score }
            .launchIn(viewModelScope)
    }

    private fun observeFinance() {
        financeRepository.observeProfile()
            .onEach { _financeProfile.value = it }
            .launchIn(viewModelScope)
            
        financeRepository.observeAllTransactions()
            .onEach { list ->
                // Calculate today's spent
                val today = DateTimeUtil.normalizeToMidnight(System.currentTimeMillis())
                val spent = list.filter { it.date >= today && (it.type.name.contains("WITHDRAWAL")) }
                    .sumOf { it.amount }
                _financeProfile.value?.let { profile ->
                    // Just triggering an update to the flow
                    _todaysSpent.value = spent
                }
            }
            .launchIn(viewModelScope)
    }

    fun refreshScore() {
        viewModelScope.launch {
            scoreRepository.calculateAndSaveDailyScore(System.currentTimeMillis())
        }
    }


    companion object {
        fun provideFactory(
            scoreRepository: ProductivityScoreRepository,
            financeRepository: FinanceRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DashboardViewModel(scoreRepository, financeRepository)
            }
        }
    }
}
