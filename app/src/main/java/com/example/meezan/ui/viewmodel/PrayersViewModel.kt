package com.example.meezan.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.meezan.data.entities.PrayerLog
import com.example.meezan.data.entities.PrayerReminderRule
import com.example.meezan.data.entities.PrayerTiming
import com.example.meezan.data.repository.PrayerTimingRepository
import com.example.meezan.data.repository.UserPreferences
import com.example.meezan.data.repository.UserPreferencesRepository
import com.example.meezan.util.AppError
import com.example.meezan.util.AppErrorException
import com.example.meezan.util.DateTimeUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds

class PrayersViewModel(
    private val prayerTimingRepository: PrayerTimingRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : BaseMeezanViewModel() {

    private val _prayerTiming = MutableStateFlow<PrayerTiming?>(null)
    val prayerTiming: StateFlow<PrayerTiming?> = _prayerTiming.asStateFlow()

    private val _prayerLogs = MutableStateFlow<List<PrayerLog>>(emptyList())
    val prayerLogs: StateFlow<List<PrayerLog>> = _prayerLogs.asStateFlow()

    private val _reminderRules = MutableStateFlow<List<PrayerReminderRule>>(emptyList())
    val reminderRules: StateFlow<List<PrayerReminderRule>> = _reminderRules.asStateFlow()

    private val _nextPrayerInfo = MutableStateFlow<NextPrayerInfo?>(null)
    val nextPrayerInfo: StateFlow<NextPrayerInfo?> = _nextPrayerInfo.asStateFlow()

    private val _activePrayerIndex = MutableStateFlow(-1)
    val activePrayerIndex: StateFlow<Int> = _activePrayerIndex.asStateFlow()

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences(0.0, 0.0, "AUTO", 2, 1))

    private val _currentDate = MutableStateFlow(DateTimeUtil.getTodayAtMidnight())
    val currentDate: StateFlow<Long> = _currentDate.asStateFlow()

    init {
        observeDateChanges()
        loadData()
        startTimers()
    }

    private fun observeDateChanges() {
        DateTimeUtil.observeCurrentDate()
            .onEach { _currentDate.value = it }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadData() {
        _currentDate.flatMapLatest { date ->
            prayerTimingRepository.observePrayerLogsForDate(date)
        }.onEach { _prayerLogs.value = it }
            .launchIn(viewModelScope)

        prayerTimingRepository.observeReminderRules()
            .onEach { _reminderRules.value = it }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val today = DateTimeUtil.getTodayAtMidnight()
            prayerTimingRepository.getPrayerTimingForDate(today)
                .onSuccess { cached ->
                    if (cached != null) {
                        updateTimings(cached)
                    } else {
                        val prefs = userPreferencesRepository.userPreferencesFlow.first()
                        if (prefs.latitude != 0.0 && prefs.longitude != 0.0) {
                            fetchTimings(prefs.latitude, prefs.longitude, prefs.calculationMethod, prefs.madhab)
                        }
                    }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startTimers() {
        _prayerTiming
            .filterNotNull()
            .flatMapLatest { timing ->
                flow {
                    while (true) {
                        emit(value = timing)
                        delay(1.seconds)
                    }
                }
            }
            .onEach { timing ->
                calculateNextPrayer(timing)
                updateActiveIndex(timing)
            }
            .launchIn(viewModelScope)
    }

    private suspend fun fetchTimings(lat: Double, lon: Double, method: Int, school: Int) {
        _isLoading.value = true
        prayerTimingRepository.fetchAndSavePrayerTimings(lat, lon, method, school)
            .onSuccess { updateTimings(it) }
            .onFailure { throwable ->
                val error = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                if (_prayerTiming.value == null) {
                    _error.value = error
                } else {
                    _snackbarError.value = error
                }
            }
        _isLoading.value = false
    }

    private fun updateTimings(timing: PrayerTiming) {
        _prayerTiming.value = timing
        calculateNextPrayer(timing)
        updateActiveIndex(timing)
    }

    fun retryFetch() {
        viewModelScope.launch {
            val prefs = userPreferences.value
            if ((prefs.latitude != 0.0) && (prefs.longitude != 0.0)) {
                fetchTimings(prefs.latitude, prefs.longitude, prefs.calculationMethod, prefs.madhab)
            } else {
                _error.value = AppError.LocationDisabled
            }
        }
    }

    fun fetchPrayerTimingsForLocation(lat: Double, lon: Double, method: Int) {
        if (lat == 0.0 && lon == 0.0) {
            _error.value = AppError.ValidationError("Invalid coordinates (0,0). Please enter a valid location.")
            return
        }
        viewModelScope.launch {
            fetchTimings(lat, lon, method, userPreferences.value.madhab)
        }
    }

    fun togglePrayer(name: String, completed: Boolean) {
        viewModelScope.launch {
            prayerTimingRepository.togglePrayer(name, completed)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun saveReminderRule(name: String, anchor: String, offset: Int, isAlarm: Boolean) {
        viewModelScope.launch {
            val existing = _reminderRules.value.find { it.prayerName == name }
            val result = if (existing == null) {
                prayerTimingRepository.addReminderRule(PrayerReminderRule(prayerName = name, anchor = anchor, offsetMinutes = offset, isAlarm = isAlarm))
            } else {
                prayerTimingRepository.updateReminderRule(existing.copy(anchor = anchor, offsetMinutes = offset, isAlarm = isAlarm))
            }
            result.onFailure { throwable ->
                _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
            }
        }
    }

    fun deleteReminderRule(rule: PrayerReminderRule) {
        viewModelScope.launch {
            prayerTimingRepository.deleteReminderRule(rule)
                .onFailure { throwable ->
                    _snackbarError.value = (throwable as? AppErrorException)?.error ?: AppError.UnknownError(throwable)
                }
        }
    }

    fun testAlarm(name: String) {
        prayerTimingRepository.triggerTestAlarm(true)
    }

    private fun updateActiveIndex(timing: PrayerTiming) {
        val cal = Calendar.getInstance()
        val nowMillis = (cal.get(Calendar.HOUR_OF_DAY) * 60 * 60 * 1000L) + 
                        (cal.get(Calendar.MINUTE) * 60 * 1000L) + 
                        (cal.get(Calendar.SECOND) * 1000L)
        _activePrayerIndex.value = listOf(timing.fajrStart, timing.dhuhrStart, timing.asrStart, timing.maghribStart, timing.ishaStart)
            .indexOfLast { it <= nowMillis }
    }

    private fun calculateNextPrayer(prayerTiming: PrayerTiming) {
        val prayers = listOf(
            Prayer("Fajr", prayerTiming.date + prayerTiming.fajrStart),
            Prayer("Dhuhr", prayerTiming.date + prayerTiming.dhuhrStart),
            Prayer("Asr", prayerTiming.date + prayerTiming.asrStart),
            Prayer("Maghrib", prayerTiming.date + prayerTiming.maghribStart),
            Prayer("Isha", prayerTiming.date + prayerTiming.ishaStart)
        ).filter { it.timeMillis > 0 }

        val now = System.currentTimeMillis()
        val nextPrayer = prayers.firstOrNull { it.timeMillis > (now + 1000) }

        if (nextPrayer != null) {
            _nextPrayerInfo.value = NextPrayerInfo(nextPrayer.name, nextPrayer.timeMillis - now)
        } else {
            val firstTomorrow = prayers.firstOrNull() ?: return
            val tomorrowPrayerTime = prayerTiming.date + (24 * 60 * 60 * 1000L) + (firstTomorrow.timeMillis - prayerTiming.date)
            _nextPrayerInfo.value = NextPrayerInfo(firstTomorrow.name, tomorrowPrayerTime - now)
        }
    }

    data class Prayer(val name: String, val timeMillis: Long)
    data class NextPrayerInfo(val name: String, val timeUntilMs: Long)

    companion object {
        fun provideFactory(
            prayerTimingRepository: PrayerTimingRepository,
            userPreferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PrayersViewModel(prayerTimingRepository, userPreferencesRepository)
            }
        }
    }
}
