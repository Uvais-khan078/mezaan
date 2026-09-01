package com.example.meezan.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.meezan.data.dao.*
import com.example.meezan.data.database.MeezanDatabase
import com.example.meezan.data.entities.FinanceProfile
import com.example.meezan.util.AppError
import com.example.meezan.util.AppErrorException
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(
    private val context: Context,
    private val database: MeezanDatabase,
    private val financeProfileDao: FinanceProfileDao,
    private val encryptedPrefs: SharedPreferences,
) : BaseRepository() {

    private val _securePrefsUpdateTrigger = MutableStateFlow(0)

    private object PreferencesKeys {
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val LOCATION_MODE = stringPreferencesKey("location_mode") // "AUTO" or "MANUAL"
        val CALCULATION_METHOD = intPreferencesKey("calculation_method")
        val MADHAB = intPreferencesKey("madhab") // 0 = Shafi, 1 = Hanafi
        val SAVED_NAMES = stringSetPreferencesKey("saved_names")
        val THEME_MODE = intPreferencesKey("theme_mode") // 0 = System, 1 = Light, 2 = Dark
        val NOTIFICATION_SOUND_URI = stringPreferencesKey("notification_sound_uri")
        val IS_ATHAN_ENABLED = booleanPreferencesKey("is_athan_enabled")
        val HAS_MIGRATED_TO_SECURE = booleanPreferencesKey("has_migrated_to_secure")
    }

    val userPreferencesFlow: Flow<UserPreferences> = combine(
        context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it },
        _securePrefsUpdateTrigger
    ) { preferences, _ ->
        // Perform migration if needed
        if (preferences[PreferencesKeys.HAS_MIGRATED_TO_SECURE] != true) {
            migrateToSecureStorage(preferences)
        }

        val latitude = encryptedPrefs.getString("latitude", "0.0")?.toDoubleOrNull() ?: 0.0
        val longitude = encryptedPrefs.getString("longitude", "0.0")?.toDoubleOrNull() ?: 0.0
        val savedNames = encryptedPrefs.getStringSet("saved_names", emptySet())?.toList() ?: emptyList()

        val locationMode = preferences[PreferencesKeys.LOCATION_MODE] ?: "AUTO"
        val calculationMethod = preferences[PreferencesKeys.CALCULATION_METHOD] ?: 2
        val madhab = preferences[PreferencesKeys.MADHAB] ?: 1
        val themeMode = preferences[PreferencesKeys.THEME_MODE] ?: 0
        val notificationSoundUri = preferences[PreferencesKeys.NOTIFICATION_SOUND_URI]
        val isAthanEnabled = preferences[PreferencesKeys.IS_ATHAN_ENABLED] ?: false
        
        UserPreferences(latitude, longitude, locationMode, calculationMethod, madhab, savedNames, themeMode, notificationSoundUri, isAthanEnabled)
    }

    private suspend fun migrateToSecureStorage(preferences: Preferences) {
        val lat = preferences[PreferencesKeys.LATITUDE] ?: 0.0
        val lon = preferences[PreferencesKeys.LONGITUDE] ?: 0.0
        val names = preferences[PreferencesKeys.SAVED_NAMES] ?: emptySet()

        encryptedPrefs.edit {
            putString("latitude", lat.toString())
            putString("longitude", lon.toString())
            putStringSet("saved_names", names)
        }

        context.dataStore.edit { it[PreferencesKeys.HAS_MIGRATED_TO_SECURE] = true }
        _securePrefsUpdateTrigger.value += 1
    }

    suspend fun updateLocation(latitude: Double, longitude: Double) = safeDbCall {
        encryptedPrefs.edit {
            putString("latitude", latitude.toString())
            putString("longitude", longitude.toString())
        }
        _securePrefsUpdateTrigger.value += 1
    }

    suspend fun updateCalculationMethod(method: Int) = safeDbCall {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CALCULATION_METHOD] = method
        }
    }

    suspend fun updateMadhab(madhab: Int) = safeDbCall {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MADHAB] = madhab
        }
    }

    suspend fun addSavedName(name: String) = safeDbCall {
        val current = encryptedPrefs.getStringSet("saved_names", emptySet()) ?: emptySet()
        encryptedPrefs.edit {
            putStringSet("saved_names", current + name)
        }
        _securePrefsUpdateTrigger.value += 1
    }

    suspend fun deleteSavedName(name: String) = safeDbCall {
        val current = encryptedPrefs.getStringSet("saved_names", emptySet()) ?: emptySet()
        encryptedPrefs.edit {
            putStringSet("saved_names", current - name)
        }
        _securePrefsUpdateTrigger.value += 1
    }

    suspend fun updateThemeMode(mode: Int) = safeDbCall {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode
        }
    }

    suspend fun updateNotificationSound(uri: String?) = safeDbCall {
        context.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[PreferencesKeys.NOTIFICATION_SOUND_URI] = uri
            } else {
                preferences.remove(PreferencesKeys.NOTIFICATION_SOUND_URI)
            }
        }
    }

    suspend fun updateAthanEnabled(enabled: Boolean) = safeDbCall {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_ATHAN_ENABLED] = enabled
        }
    }

    suspend fun clearAllActivityData(includeFinance: Boolean): Result<String> = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                database.prayerLogDao().deleteAllLogs()
                database.goalDao().deleteAllGoals()
                database.goalTaskDao().deleteAllTasks()
                database.focusSessionDao().deleteAllSessions()
                database.habitDao().deleteAllHabits()
                database.habitLogDao().deleteAllLogs()
                database.dailyScoreDao().deleteAllScores()

                if (includeFinance) {
                    database.transactionDao().deleteAllTransactions()
                    database.lendingDao().deleteAllRecords()
                    database.financeProfileDao().deleteProfile()
                    financeProfileDao.updateProfile(FinanceProfile(id = 1, totalMonthlyBudget = 0.0, totalSavings = 0.0, lastUpdateDate = 0))
                }
                
                // Re-seed prayer habits after clearing
                seedPrayerHabitsInternal()
            }
            _securePrefsUpdateTrigger.value += 1
            Result.success("Activity data cleared.")
        } catch (e: Exception) {
            android.util.Log.e("UserPrefsRepo", "Failed to clear data", e)
            Result.failure(AppErrorException(AppError.DatabaseError(e)))
        }
    }

    private suspend fun seedPrayerHabitsInternal() {
        val prayerNames = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        prayerNames.forEach { name ->
            database.habitDao().insert(
                com.example.meezan.data.entities.Habit(
                    name = name,
                    icon = "🕌",
                    createdDate = System.currentTimeMillis(),
                    isDeletable = false
                )
            )
        }
    }
}

data class UserPreferences(
    val latitude: Double,
    val longitude: Double,
    val locationMode: String,
    val calculationMethod: Int,
    val madhab: Int,
    val savedNames: List<String> = emptyList(),
    val themeMode: Int = 0,
    val notificationSoundUri: String? = null,
    val isAthanEnabled: Boolean = false
)
