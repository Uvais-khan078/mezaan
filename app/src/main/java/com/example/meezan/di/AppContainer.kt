package com.example.meezan.di

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.meezan.data.api.AladhanApiService
import com.example.meezan.data.database.MeezanDatabase
import com.example.meezan.data.dao.*
import com.example.meezan.data.repository.*
import com.example.meezan.data.scheduler.ReminderScheduler
import com.google.android.gms.location.LocationServices
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * AppContainer provides all dependencies for the app
 * Implements a simple service-locator pattern for dependency injection
 */
class AppContainer(private val context: Context) {

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "secure_user_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val database by lazy {
        val key = "db_passphrase"
        var passphrase = encryptedPrefs.getString(key, null)
        if (passphrase == null) {
            passphrase = java.util.UUID.randomUUID().toString()
            encryptedPrefs.edit().putString(key, passphrase).apply()
        }
        MeezanDatabase.getDatabase(context, passphrase.toByteArray())
    }

    val prayerTimingDao: PrayerTimingDao by lazy {
        database.prayerTimingDao()
    }

    val prayerLogDao: PrayerLogDao by lazy {
        database.prayerLogDao()
    }

    val prayerReminderRuleDao: PrayerReminderRuleDao by lazy {
        database.prayerReminderRuleDao()
    }

    val goalDao: GoalDao by lazy {
        database.goalDao()
    }

    val goalTaskDao: GoalTaskDao by lazy {
        database.goalTaskDao()
    }

    val focusSessionDao: FocusSessionDao by lazy {
        database.focusSessionDao()
    }

    val habitDao: HabitDao by lazy {
        database.habitDao()
    }

    val habitLogDao: HabitLogDao by lazy {
        database.habitLogDao()
    }

    val dailyScoreDao: DailyScoreDao by lazy {
        database.dailyScoreDao()
    }

    val transactionDao: FinanceTransactionDao by lazy {
        database.transactionDao()
    }

    val lendingDao: LendingDao by lazy {
        database.lendingDao()
    }

    val financeProfileDao: FinanceProfileDao by lazy {
        database.financeProfileDao()
    }

    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .apply {
                if (com.example.meezan.BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                }
            }
            .build()
    }

    val aladhanApiService: AladhanApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AladhanApiService.BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(okHttpClient)
            .build()
            .create(AladhanApiService::class.java)
    }

    val prayerTimingRepository by lazy {
        PrayerTimingRepository(
            prayerTimingDao, 
            prayerLogDao, 
            prayerReminderRuleDao, 
            reminderScheduler, 
            aladhanApiService
        )
    }

    val productivityScoreRepository by lazy {
        ProductivityScoreRepository(
            prayerLogDao,
            goalTaskDao,
            focusSessionDao,
            habitDao,
            habitLogDao,
            dailyScoreDao
        )
    }

    val goalRepository by lazy {
        GoalRepository(database, goalDao, goalTaskDao, reminderScheduler)
    }

    val habitRepository by lazy {
        HabitRepository(database, habitDao, habitLogDao, reminderScheduler)
    }

    val financeRepository by lazy {
        FinanceRepository(database, transactionDao, lendingDao, financeProfileDao)
    }

    val userPreferencesRepository by lazy {
        UserPreferencesRepository(
            context,
            database,
            financeProfileDao,
            encryptedPrefs
        )
    }

    val dataExportRepository by lazy {
        DataExportRepository(
            goalDao, goalTaskDao, habitDao, habitLogDao, focusSessionDao, moshi
        )
    }

    val reminderScheduler by lazy {
        ReminderScheduler(context)
    }

    val focusRepository by lazy {
        FocusRepository(goalTaskDao, focusSessionDao)
    }

    val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
}
