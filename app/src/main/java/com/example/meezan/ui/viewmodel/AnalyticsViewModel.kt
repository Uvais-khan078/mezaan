package com.example.meezan.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.meezan.data.entities.DailyScore
import com.example.meezan.data.entities.HabitLog
import com.example.meezan.data.entities.TransactionType
import com.example.meezan.data.repository.FinanceRepository
import com.example.meezan.data.repository.HabitRepository
import com.example.meezan.data.repository.ProductivityScoreRepository
import com.example.meezan.util.Constants
import com.example.meezan.util.DateTimeUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.Calendar

enum class TimeRange(val days: Int) {
    WEEK(days = Constants.Analytics.WEEK_DAYS),
    MONTH(days = Constants.Analytics.MONTH_DAYS),
}

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    private val scoreRepository: ProductivityScoreRepository,
    private val financeRepository: FinanceRepository,
    private val habitRepository: HabitRepository,
) : BaseMeezanViewModel() {

    private val _selectedRange = MutableStateFlow(TimeRange.WEEK)
    val selectedRange: StateFlow<TimeRange> = _selectedRange.asStateFlow()

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        observeTrends()
    }

    fun setRange(range: TimeRange) {
        _selectedRange.value = range
    }

    fun retry() {
        _isLoading.value = true
        observeTrends()
    }

    private fun observeTrends() {
        _selectedRange.flatMapLatest { range ->
            val cal = Calendar.getInstance()
            val endDate = cal.timeInMillis
            
            val startDate = if (range == TimeRange.MONTH) {
                // Align to the 1st of the current month
                cal.apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                }.timeInMillis.let { DateTimeUtil.normalizeToMidnight(it) }
            } else {
                // Align to the current week's Monday
                cal.apply {
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    // If it's Sunday today, we might want this Monday or last Monday?
                    // Standard practice is current week's Monday. 
                    // If today is before Monday (Sun), Calendar might jump forward.
                    if (timeInMillis > endDate) {
                        add(Calendar.WEEK_OF_YEAR, -1)
                    }
                }.timeInMillis.let { DateTimeUtil.normalizeToMidnight(it) }
            }

            // Calculate exact number of days to display
            val diffMillis = endDate - startDate
            val daysToDisplay = (diffMillis / (24 * 60 * 60 * 1000) + 1).toInt().coerceAtLeast(1)

            combine(
                scoreRepository.observeScoresInRange(startDate, endDate),
                financeRepository.observeTransactionsInRange(startDate, endDate),
                habitRepository.observeLogsInRange(startDate, endDate),
            ) { scores, transactionsInRange, habitLogs ->
                
                val expenses = transactionsInRange.filter { 
                    (it.type == TransactionType.WITHDRAWAL) || (it.type == TransactionType.SAVINGS_WITHDRAWAL) 
                }

                val distribution = expenses.groupBy { it.sourceOrReason.lowercase() }
                    .mapValues { entry -> entry.value.sumOf { it.amount } }

                // PADDING LOGIC: Ensure every day has a data point
                val paddedScores = mutableListOf<DailyScore>()
                val paddedSpending = mutableMapOf<Long, Double>()
                val dateLabels = mutableListOf<String>()
                
                val currentCal = Calendar.getInstance().apply { timeInMillis = startDate }
                val scoreMap = scores.associateBy { it.date }
                val spendingMap = expenses.groupBy { DateTimeUtil.normalizeToMidnight(it.date) }
                    .mapValues { entry -> entry.value.sumOf { it.amount } }

                repeat(daysToDisplay) {
                    val dateMillis = currentCal.timeInMillis
                    
                    // 1. Label
                    dateLabels.add(DateTimeUtil.formatShortDate(dateMillis))
                    
                    // 2. Score (Pad with 0 if missing)
                    paddedScores.add(scoreMap[dateMillis] ?: DailyScore(dateMillis))
                    
                    // 3. Spending (Pad with 0 if missing)
                    paddedSpending[dateMillis] = spendingMap[dateMillis] ?: 0.0
                    
                    currentCal.add(Calendar.DAY_OF_YEAR, 1)
                }

                val avgScore = if (scores.isEmpty()) 0f else {
                    scores.asSequence().map { it.overallScore }.average().toFloat()
                }
                val topCategory = distribution.maxByOrNull { it.value }?.key
                val bestDay = scores.maxByOrNull { it.overallScore }?.date

                AnalyticsUiState(
                    scoreHistory = paddedScores,
                    expenseDistribution = distribution,
                    dailySpending = paddedSpending,
                    habitHistory = habitLogs,
                    dateLabels = dateLabels,
                    averageScore = avgScore,
                    topCategory = topCategory,
                    bestDay = bestDay
                )
            }
        }.onEach { newState: AnalyticsUiState ->
            _uiState.value = newState
            _isLoading.value = false
        }.catch { e ->
            _error.value = com.example.meezan.util.AppError.UnknownError(e)
            _isLoading.value = false
        }.launchIn(viewModelScope)
    }


    companion object {
        fun provideFactory(
            scoreRepository: ProductivityScoreRepository,
            financeRepository: FinanceRepository,
            habitRepository: HabitRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AnalyticsViewModel(scoreRepository, financeRepository, habitRepository)
            }
        }
    }
}

data class AnalyticsUiState(
    val scoreHistory: List<DailyScore> = emptyList(),
    val expenseDistribution: Map<String, Double> = emptyMap(),
    val dailySpending: Map<Long, Double> = emptyMap(),
    val habitHistory: List<HabitLog> = emptyList(),
    val dateLabels: List<String> = emptyList(),
    val averageScore: Float = 0f,
    val topCategory: String? = null,
    val bestDay: Long? = null
)
