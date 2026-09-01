package com.example.meezan.data.repository
import com.example.meezan.data.api.AladhanApiService
import com.example.meezan.data.api.models.*
import com.example.meezan.data.dao.PrayerLogDao
import com.example.meezan.data.dao.PrayerReminderRuleDao
import com.example.meezan.data.dao.PrayerTimingDao
import com.example.meezan.data.entities.PrayerLog
import com.example.meezan.data.entities.PrayerReminderRule
import com.example.meezan.data.entities.PrayerTiming
import com.example.meezan.data.scheduler.ReminderScheduler
import com.example.meezan.util.AppError
import com.example.meezan.util.AppErrorException
import com.example.meezan.util.Constants
import com.example.meezan.util.DateTimeUtil
import kotlinx.coroutines.flow.Flow
/**
 * Repository for managing prayer timings
 */
class PrayerTimingRepository(
    private val prayerTimingDao: PrayerTimingDao,
    private val prayerLogDao: PrayerLogDao,
    private val prayerReminderRuleDao: PrayerReminderRuleDao,
    private val reminderScheduler: ReminderScheduler,
    private val aladhanApiService: AladhanApiService
) : BaseRepository() {
    
    fun observePrayerLogsForDate(date: Long): Flow<List<PrayerLog>> = 
        prayerLogDao.observeLogsForDate(DateTimeUtil.normalizeToMidnight(date))

    suspend fun togglePrayer(prayerName: String, completed: Boolean): Result<Unit> = safeDbCall {
        prayerLogDao.insert(
            PrayerLog(
                date = DateTimeUtil.getTodayAtMidnight(),
                prayerName = prayerName,
                completed = completed
            )
        )
    }

    fun observeReminderRules(): Flow<List<PrayerReminderRule>> = 
        prayerReminderRuleDao.observeAllRules()

    suspend fun getEnabledReminderRules(): Result<List<PrayerReminderRule>> = safeDbCall {
        prayerReminderRuleDao.getEnabledRules()
    }

    suspend fun addReminderRule(rule: PrayerReminderRule): Result<Unit> = safeDbCall {
        prayerReminderRuleDao.insert(rule)
        // Re-sync alarms for today
        getPrayerTimingForDate(System.currentTimeMillis()).onSuccess { it?.let { rescheduleEnabledPrayerReminders(it) } }
    }

    suspend fun updateReminderRule(rule: PrayerReminderRule): Result<Unit> = safeDbCall {
        prayerReminderRuleDao.update(rule)
        if (rule.enabled) {
            getPrayerTimingForDate(System.currentTimeMillis()).onSuccess { it?.let { reminderScheduler.schedulePrayerReminder(rule, it) } }
        } else {
            reminderScheduler.cancelPrayerReminder(rule.id.toInt())
        }
    }

    suspend fun deleteReminderRule(rule: PrayerReminderRule): Result<Unit> = safeDbCall {
        prayerReminderRuleDao.delete(rule)
        reminderScheduler.cancelPrayerReminder(rule.id.toInt())
    }

    suspend fun rescheduleEnabledPrayerReminders(prayerTiming: PrayerTiming): Result<Unit> = safeDbCall {
        val rules = prayerReminderRuleDao.getEnabledRules()
        rules.forEach { rule -> reminderScheduler.schedulePrayerReminder(rule, prayerTiming) }
    }

    suspend fun cancelReminder(ruleId: Int): Result<Unit> = safeDbCall {
        reminderScheduler.cancelPrayerReminder(ruleId)
    }

    fun triggerTestAlarm(isAlarm: Boolean): Result<Unit> {
        return try {
            reminderScheduler.triggerTestNotification(isAlarm)
            Result.success(Unit)
        } catch (e: AppErrorException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(AppErrorException(AppError.UnknownError(e)))
        }
    }
    /**
     * Fetch prayer timings from API and store in local database
     */
    suspend fun fetchAndSavePrayerTimings(
        latitude: Double,
        longitude: Double,
        method: Int = Constants.DEFAULT_CALCULATION_METHOD,
        school: Int = Constants.DEFAULT_MADHAB
    ): Result<PrayerTiming> = safeApiCall {
        val calendar = java.util.Calendar.getInstance()
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val year = calendar.get(java.util.Calendar.YEAR)

        val response = aladhanApiService.getPrayerCalendar(
            latitude = latitude,
            longitude = longitude,
            method = method,
            month = month,
            year = year,
            school = school
        )

        if (response.code == 200) {
            val days = response.data
            // Sanity check: prevent processing massive payloads
            if (days.size > 31) {
                throw Exception("Unexpected payload size: ${days.size} days")
            }

            var todayTiming: PrayerTiming? = null
            val todayAtMidnight = DateTimeUtil.normalizeToMidnight(System.currentTimeMillis())

            for (dayData in days) {
                val apiTimestamp = dayData.date.timestamp.toLongOrNull() ?: 0L
                val prayerDate = DateTimeUtil.normalizeToMidnight(apiTimestamp * 1000)
                val prayerTiming = parsePrayerTiming(
                    timings = dayData.timings,
                    latitude = latitude,
                    longitude = longitude,
                    method = method,
                    prayerDate = prayerDate
                )
                prayerTimingDao.insert(prayerTiming)
                if (prayerDate == todayAtMidnight) {
                    todayTiming = prayerTiming
                }
            }
            
            todayTiming ?: prayerTimingRepository_getTodayTimingFallback(latitude, longitude, method, school)
                ?: throw Exception("Failed to obtain prayer timings")
        } else {
            throw Exception("API Error: ${response.status}")
        }
    }

    private suspend fun prayerTimingRepository_getTodayTimingFallback(lat: Double, lon: Double, method: Int, school: Int): PrayerTiming? {
        val response = aladhanApiService.getPrayerTimings(
            latitude = lat,
            longitude = lon,
            method = method,
            school = school
        )
        return if (response.code == 200) {
            val apiTimestamp = response.data.date.timestamp.toLongOrNull() ?: (System.currentTimeMillis() / 1000)
            val prayerDate = DateTimeUtil.normalizeToMidnight(apiTimestamp * 1000)
            val timing = parsePrayerTiming(
                timings = response.data.timings,
                latitude = lat,
                longitude = lon,
                method = method,
                prayerDate = prayerDate
            )
            prayerTimingDao.insert(timing)
            timing
        } else null
    }
    /**
     * Get prayer timing for a specific date from database
     */
    suspend fun getPrayerTimingForDate(date: Long): Result<PrayerTiming?> = safeDbCall {
        prayerTimingDao.getPrayerTimingForDate(DateTimeUtil.normalizeToMidnight(date))
    }
    /**
     * Get latest stored prayer timing
     */
    suspend fun getLatestPrayerTiming(): Result<PrayerTiming?> = safeDbCall {
        prayerTimingDao.getLatestPrayerTiming()
    }
    /**
     * Parse prayer timings from API response
     * Expected format: "05:31 (GMT)" or "05:31"
     */
    private fun parsePrayerTiming(
        timings: Map<String, String>,
        latitude: Double,
        longitude: Double,
        method: Int,
        prayerDate: Long
    ): PrayerTiming {
        // Sanity check: coordinates
        if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) {
            throw Exception("Invalid coordinates: $latitude, $longitude")
        }

        fun parseTime(timeString: String): Long {
            // Format: "HH:mm (GMT)" or "HH:mm"
            val cleanTime = timeString.split("(")[0].trim()
            val parts = cleanTime.split(":")
            if (parts.size == 2) {
                val hours = parts[0].toIntOrNull() ?: 0
                val minutes = parts[1].toIntOrNull() ?: 0
                return (hours * 60L + minutes) * 60 * 1000 // Convert to milliseconds since midnight
            }
            return 0L
        }
        return PrayerTiming(
            date = prayerDate,
            latitude = latitude,
            longitude = longitude,
            method = method.toString(),
            fajrStart = parseTime(timings["Fajr"] ?: ""),
            fajrEnd = parseTime(timings["Sunrise"] ?: ""),
            sunrise = parseTime(timings["Sunrise"] ?: ""),
            dhuhrStart = parseTime(timings["Dhuhr"] ?: ""),
            dhuhrEnd = parseTime(timings["Asr"] ?: ""),
            asrStart = parseTime(timings["Asr"] ?: ""),
            asrEnd = parseTime(timings["Sunset"] ?: ""),
            sunset = parseTime(timings["Sunset"] ?: ""),
            maghribStart = parseTime(timings["Maghrib"] ?: ""),
            maghribEnd = parseTime(timings["Isha"] ?: ""),
            ishaStart = parseTime(timings["Isha"] ?: ""),
            ishaEnd = (24 * 60 + 240) * 60 * 1000L // Approx 4 AM next day in millis since midnight
        )
    }
}
