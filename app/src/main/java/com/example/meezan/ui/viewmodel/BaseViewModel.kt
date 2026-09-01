package com.example.meezan.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.example.meezan.util.AppError

/**
 * Base ViewModel to handle common UI states like loading, error, and success messages.
 */
abstract class BaseMeezanViewModel : ViewModel() {
    protected val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    protected val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    protected val _snackbarError = MutableStateFlow<AppError?>(null)
    val snackbarError: StateFlow<AppError?> = _snackbarError.asStateFlow()

    protected val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun clearSnackbarError() {
        _snackbarError.value = null
    }

    fun clearSuccess() {
        _success.value = null
    }
}
