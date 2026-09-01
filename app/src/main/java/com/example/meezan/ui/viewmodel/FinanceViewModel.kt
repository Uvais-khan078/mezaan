package com.example.meezan.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.meezan.data.entities.*
import com.example.meezan.data.repository.FinanceRepository
import com.example.meezan.data.repository.UserPreferencesRepository
import com.example.meezan.util.AppError
import com.example.meezan.util.AppErrorException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FinanceViewModel(
    private val financeRepository: FinanceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : BaseMeezanViewModel() {

    private val _uiState = MutableStateFlow(FinanceUiState())
    val uiState: StateFlow<FinanceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Ensure profile exists
            financeRepository.observeProfile().first() ?: financeRepository.addBudget(0.0, "Initial")
        }
        observeData()
    }

    private fun observeData() {
        financeRepository.observeAllTransactions()
            .onEach { list -> _uiState.update { it.copy(transactions = list) } }
            .launchIn(viewModelScope)

        financeRepository.observeActiveLending()
            .onEach { list -> _uiState.update { it.copy(lending = list) } }
            .launchIn(viewModelScope)

        financeRepository.observeProfile()
            .onEach { profile -> 
                _uiState.update { it.copy(profile = profile) }
                updateTodaysSavings()
            }
            .launchIn(viewModelScope)

        userPreferencesRepository.userPreferencesFlow
            .onEach { prefs -> _uiState.update { it.copy(savedNames = prefs.savedNames) } }
            .launchIn(viewModelScope)
            
        financeRepository.observeAllTransactions()
            .onEach { updateTodaysSavings() }
            .launchIn(viewModelScope)
    }

    private fun updateTodaysSavings() {
        viewModelScope.launch {
            val savings = financeRepository.getTodaysSavings()
            _uiState.update { it.copy(todaysSavings = savings) }
        }
    }

    fun addBudget(amount: Double, source: String) {
        if (amount <= 0) {
            _snackbarError.value = AppError.ValidationError("Amount must be positive")
            return
        }
        if (amount > 1_000_000_000_000.0) {
            _snackbarError.value = AppError.ValidationError("Amount is too large. Max limit is 1 Trillion.")
            return
        }
        viewModelScope.launch {
            financeRepository.addBudget(amount, source)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun withdraw(
        amount: Double, 
        reason: String, 
        isSplit: Boolean = false, 
        isDebt: Boolean = false,
        people: List<String> = emptyList()
    ) {
        if (amount <= 0) {
            _snackbarError.value = AppError.ValidationError("Amount must be positive")
            return
        }
        if (amount > 1_000_000_000_000.0) {
            _snackbarError.value = AppError.ValidationError("Amount is too large. Max limit is 1 Trillion.")
            return
        }
        if ((isSplit || isDebt) && people.isEmpty()) {
            _snackbarError.value = AppError.ValidationError("Please select or add at least one person for splits or debt.")
            return
        }
        viewModelScope.launch {
            financeRepository.withdraw(amount, reason, isSplit, isDebt, people)
                .onSuccess {
                    people.forEach { name -> userPreferencesRepository.addSavedName(name) }
                }
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun clearLending(record: LendingRecord) {
        viewModelScope.launch {
            financeRepository.clearLending(record)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun addToSavings(amount: Double) {
        viewModelScope.launch {
            financeRepository.addToSavings(amount)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun transferFromSavings(amount: Double) {
        viewModelScope.launch {
            financeRepository.transferFromSavings(amount)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    companion object {
        fun provideFactory(financeRepository: FinanceRepository, userPreferencesRepository: UserPreferencesRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FinanceViewModel(financeRepository, userPreferencesRepository)
            }
        }
    }
}

data class FinanceUiState(
    val transactions: List<FinanceTransaction> = emptyList(),
    val lending: List<LendingRecord> = emptyList(),
    val profile: FinanceProfile? = null,
    val savedNames: List<String> = emptyList(),
    val todaysSavings: Double = 0.0
)
