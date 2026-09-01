package com.example.meezan.data.repository

import com.example.meezan.data.dao.HabitDao
import com.example.meezan.data.dao.HabitLogDao
import com.example.meezan.data.database.MeezanDatabase
import com.example.meezan.data.entities.Habit
import com.example.meezan.data.entities.HabitLog
import com.example.meezan.data.entities.HabitWithHistory
import com.example.meezan.data.scheduler.ReminderScheduler
import com.example.meezan.util.DateTimeUtil
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class HabitRepository(
    private val database: MeezanDatabase,
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
    private val reminderScheduler: ReminderScheduler
) : BaseRepository() {
    fun observeActiveHabits(): Flow<List<Habit>> = habitDao.observeActiveHabits()

    suspend fun seedPrayerHabits() = safeDbCall {
        val currentHabits = habitDao.getAllHabits()
        val prayerNames = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        
        prayerNames.forEach { name ->
            if (currentHabits.none { it.name == name }) {
                habitDao.insert(
                    Habit(
                        name = name,
                        icon = "🕌",
                        createdDate = System.currentTimeMillis(),
                        isDeletable = false
                    )
                )
            }
        }
    }

    suspend fun addHabit(name: String, icon: String = ""): Result<Unit> = safeDbCall {
        val habit = Habit(
            name = name,
            icon = icon,
            createdDate = System.currentTimeMillis()
        )
        habitDao.insert(habit)
    }

    suspend fun toggleHabit(habitId: Long, date: Long, completed: Boolean): Result<Unit> = safeDbCall {
        val normalizedDate = DateTimeUtil.normalizeToMidnight(date)
        val existingLog = habitLogDao.getLogForHabitDate(habitId, normalizedDate)
        
        if (existingLog != null) {
            habitLogDao.update(existingLog.copy(completed = completed))
        } else {
            habitLogDao.insert(
                HabitLog(
                    habitId = habitId,
                    date = normalizedDate,
                    completed = completed
                )
            )
        }
    }

    suspend fun isHabitCompletedForDate(habitId: Long, date: Long): Result<Boolean> = safeDbCall {
        habitLogDao.isHabitCompletedForDate(habitId, DateTimeUtil.normalizeToMidnight(date)) > 0
    }

    fun observeLogsForDate(date: Long): Flow<List<HabitLog>> = 
        habitLogDao.observeLogsForDate(DateTimeUtil.normalizeToMidnight(date))

    fun observeLogsInRange(startDate: Long, endDate: Long): Flow<List<HabitLog>> =
        habitLogDao.observeLogsInRange(DateTimeUtil.normalizeToMidnight(startDate), DateTimeUtil.normalizeToMidnight(endDate))

    suspend fun getHabitsWithHistory(habits: List<Habit>): List<HabitWithHistory> {
        if (habits.isEmpty()) return emptyList()
        
        val today = DateTimeUtil.getTodayAtMidnight()
        val cal = Calendar.getInstance()
        cal.timeInMillis = today
        cal.add(Calendar.DAY_OF_YEAR, -30) // Get enough for streak + 7-day display
        val startDate = cal.timeInMillis
        
        val allLogs = habitLogDao.getLogsForHabits(habits.map { it.id }, startDate)
        val logsByHabit = allLogs.groupBy { it.habitId }
        
        return habits.map { habit ->
            val habitLogs = logsByHabit[habit.id] ?: emptyList()
            val completedDates = habitLogs.filter { it.completed }.map { it.date }.toSet()
            
            // Calculate 7-day history
            val history = (0..6).map { dayOffset ->
                val dateCal = Calendar.getInstance()
                dateCal.timeInMillis = today
                dateCal.add(Calendar.DAY_OF_YEAR, -dayOffset)
                completedDates.contains(dateCal.timeInMillis)
            }.reversed()
            
            // Calculate Streak
            var streak = 0
            var checkDate = if (completedDates.contains(today)) today else {
                val yesterdayCal = Calendar.getInstance()
                yesterdayCal.timeInMillis = today
                yesterdayCal.add(Calendar.DAY_OF_YEAR, -1)
                yesterdayCal.timeInMillis
            }
            
            while (completedDates.contains(checkDate)) {
                streak++
                val nextCal = Calendar.getInstance()
                nextCal.timeInMillis = checkDate
                nextCal.add(Calendar.DAY_OF_YEAR, -1)
                checkDate = nextCal.timeInMillis
                if (streak > 365) break // Safety cap
            }
            
            HabitWithHistory(habit, streak, history)
        }
    }

    suspend fun getStreak(habitId: Long): Int {
        // Simple streak calculation: count consecutive completed days starting from today/yesterday
        var streak = 0
        val cal = Calendar.getInstance()
        
        // Start from today or yesterday depending on if today is already completed
        val today = DateTimeUtil.normalizeToMidnight(System.currentTimeMillis())
        val todayCompleted = habitLogDao.isHabitCompletedForDate(habitId, today) > 0
        
        var checkDate = if (todayCompleted) today else {
            cal.timeInMillis = today
            cal.add(Calendar.DAY_OF_YEAR, -1)
            cal.timeInMillis
        }

        var maxIterations = 365 // Safety cap for 1 year
        while (maxIterations > 0) {
            if (habitLogDao.isHabitCompletedForDate(habitId, checkDate) > 0) {
                streak++
                cal.timeInMillis = checkDate
                cal.add(Calendar.DAY_OF_YEAR, -1)
                checkDate = cal.timeInMillis
                maxIterations--
            } else {
                break
            }
        }
        return streak
    }

    suspend fun deleteHabit(habit: Habit): Result<Unit> = safeDbCall {
        database.withTransaction {
            reminderScheduler.cancelReminder(habit.id.toInt(), ReminderScheduler.ReminderType.HABIT)
            habitLogDao.deleteLogsByHabitId(habit.id)
            habitDao.delete(habit)
        }
    }

    suspend fun updateHabit(habit: Habit): Result<Unit> = safeDbCall {
        habitDao.update(habit)
    }

    suspend fun setHabitReminder(habitId: Long, hour: Int, minute: Int, enabled: Boolean): Result<Unit> = safeDbCall {
        val habit = habitDao.getHabitById(habitId) ?: throw Exception("Habit not found")
        val reminderTime = (hour * 60L + minute) * 60 * 1000 // Millis since midnight
        val updated = habit.copy(
            reminderTime = if (enabled) reminderTime else null,
            isReminderEnabled = enabled
        )
        habitDao.update(updated)

        if (enabled) {
            // Schedule for today/tomorrow
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            reminderScheduler.scheduleReminder(
                reminderTime = cal.timeInMillis,
                reminderId = habitId.toInt(),
                reminderType = ReminderScheduler.ReminderType.HABIT
            )
        } else {
            reminderScheduler.cancelReminder(habitId.toInt(), ReminderScheduler.ReminderType.HABIT)
        }
    }

}
