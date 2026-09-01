package com.example.meezan.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.meezan.data.repository.DataExportRepository
import com.example.meezan.data.repository.PrayerTimingRepository
import com.example.meezan.data.repository.UserPreferences
import com.example.meezan.data.repository.UserPreferencesRepository
import com.example.meezan.util.AppError
import com.example.meezan.util.AppErrorException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val prayerTimingRepository: PrayerTimingRepository,
    private val dataExportRepository: DataExportRepository,
    private val fusedLocationClient: FusedLocationProviderClient
) : BaseMeezanViewModel() {

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences(0.0, 0.0, "AUTO", 2, 1))

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { 
            userPreferencesRepository.updateThemeMode(mode)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun setMadhab(school: Int) {
        viewModelScope.launch { 
            userPreferencesRepository.updateMadhab(school)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun setCalculationMethod(method: Int) {
        viewModelScope.launch { 
            userPreferencesRepository.updateCalculationMethod(method)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun detectLocation() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token
                ).await()
                if (location != null) {
                    userPreferencesRepository.updateLocation(location.latitude, location.longitude)
                        .onSuccess {
                            prayerTimingRepository.fetchAndSavePrayerTimings(location.latitude, location.longitude)
                                .onFailure { throwable ->
                                    _error.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                                }
                        }
                        .onFailure { throwable ->
                            _error.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                        }
                } else {
                    _error.value = AppError.LocationDisabled
                }
            } catch (_: SecurityException) {
                _error.value = AppError.LocationPermissionDenied
            } catch (e: Exception) {
                _error.value = AppError.UnknownError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setManualLocation(lat: Double, lon: Double) {
        if (lat == 0.0 && lon == 0.0) {
            _snackbarError.value = AppError.ValidationError("Invalid coordinates (0,0). Please enter a valid location.")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            userPreferencesRepository.updateLocation(lat, lon)
                .onSuccess {
                    prayerTimingRepository.fetchAndSavePrayerTimings(lat, lon)
                        .onFailure { throwable ->
                            _error.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                        }
                }
                .onFailure { throwable ->
                    _error.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
            _isLoading.value = false
        }
    }

    fun setAthanEnabled(enabled: Boolean) {
        viewModelScope.launch { 
            userPreferencesRepository.updateAthanEnabled(enabled)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun setNotificationSound(uri: Uri?) {
        viewModelScope.launch { 
            userPreferencesRepository.updateNotificationSound(uri?.toString())
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun triggerTestAlarm(isAlarm: Boolean) {
        prayerTimingRepository.triggerTestAlarm(isAlarm)
            .onFailure { throwable ->
                _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
            }
    }

    fun deleteSavedName(name: String) {
        viewModelScope.launch { 
            userPreferencesRepository.deleteSavedName(name)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun exportData(context: Context, onFileReady: (File) -> Unit) {
        viewModelScope.launch {
            dataExportRepository.exportDataToJson()
                .onSuccess { json ->
                    try {
                        val file = File(context.cacheDir, "meezan_backup_${System.currentTimeMillis()}.json")
                        file.writeText(json)
                        onFileReady(file)
                    } catch (e: Exception) {
                        _error.value = AppError.UnknownError(e)
                    }
                }
                .onFailure { throwable ->
                    _error.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun importData(json: String) {
        viewModelScope.launch {
            dataExportRepository.importDataFromJson(json)
                .onSuccess { _success.value = "Imported successfully. Please restart." }
                .onFailure { throwable ->
                    val error = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                    _error.value = error
                }
        }
    }

    fun clearAllData(includeFinance: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.clearAllActivityData(includeFinance)
                .onSuccess { _success.value = it }
                .onFailure { throwable ->
                    val error = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                    _error.value = error
                }
        }
    }

    companion object {
        fun provideFactory(
            userPreferencesRepository: UserPreferencesRepository,
            prayerTimingRepository: PrayerTimingRepository,
            dataExportRepository: DataExportRepository,
            fusedLocationClient: FusedLocationProviderClient
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(userPreferencesRepository, prayerTimingRepository, dataExportRepository, fusedLocationClient)
            }
        }
    }
}
