package com.example.meezan.data.repository

import com.example.meezan.data.dao.*
import com.example.meezan.data.entities.DailyScore
import com.example.meezan.data.entities.TaskStatus
import com.example.meezan.util.Constants
import com.example.meezan.util.DateTimeUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Calendar

class ProductivityScoreRepository(
    private val prayerLogDao: PrayerLogDao,
    private val goalTaskDao: GoalTaskDao,
    private val focusSessionDao: FocusSessionDao,
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
    private val dailyScoreDao: DailyScoreDao
) : BaseRepository() {

    suspend fun calculateAndSaveDailyScore(date: Long): Result<Unit> = safeDbCall {
        val normalizedDate = DateTimeUtil.normalizeToMidnight(date)
        
        // Fetch all habits and logs to calculate unified score
        val habits = habitDao.getAllHabits()
        val logs = habitLogDao.observeLogsForDate(normalizedDate).first()
        val completedHabitIds = logs.filter { it.completed }.map { it.habitId }.toSet()
        
        val prayerNames = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        val prayerHabits = habits.filter { it.name in prayerNames }
        
        // 1. Prayer Score (based on Habit completion for namaz)
        val prayerScore = if (prayerHabits.isEmpty()) 0f else {
            val completedCount = prayerHabits.count { completedHabitIds.contains(it.id) }
            (completedCount.toFloat() / prayerNames.size.toFloat()) * 100f
        }

        // 2. Goal Completion Score
        val tasksForToday = goalTaskDao.getTasksForDate(normalizedDate)
        val validTasks = tasksForToday.filter { it.status != TaskStatus.SKIPPED }
        val completedTasks = validTasks.count { it.status == TaskStatus.COMPLETED }
        val goalScore = if (validTasks.isEmpty()) 100f else (completedTasks.toFloat() / validTasks.size.toFloat()) * 100f

        // 3. Focus Score
        val taskIds = validTasks.map { it.id }
        val focusDurations = if (taskIds.isEmpty()) emptyList() else focusSessionDao.getTotalDurationsForTasks(taskIds)
        val focusMap = focusDurations.associate { it.taskId to it.duration }

        val totalRequired = validTasks.sumOf { it.requiredDurationMinutes }
        val totalActual = validTasks.sumOf { focusMap[it.id] ?: 0 }
        
        val focusScore = if (totalRequired == 0) 100f else {
            (totalActual.toFloat() / totalRequired.toFloat() * 100f).coerceAtMost(100f)
        }

        // 4. Habit Score (excluding prayers to avoid double counting)
        val customHabits = habits.filter { it.name !in prayerNames }
        val habitScore: Float? = if (customHabits.isEmpty()) null else {
            val completedCount = customHabits.count { completedHabitIds.contains(it.id) }
            (completedCount.toFloat() / customHabits.size.toFloat()) * 100f
        }

        // Calculate Overall Score (Fair weighted average)
        val activePillars = mutableListOf<Float>()
        
        // Prayers: Always active for Meezan
        activePillars.add(prayerScore)
        
        // Goals: Only if tasks were actually scheduled for today
        if (validTasks.isNotEmpty()) activePillars.add(goalScore)
        
        // Focus: Only if there were tasks with durations
        if (totalRequired > 0) activePillars.add(focusScore)
        
        // Habits: Only if any custom habits were active
        habitScore?.let { activePillars.add(it) }

        val overallScore = if (activePillars.isEmpty()) 0f else activePillars.average().toFloat()

        val score = DailyScore(
            date = normalizedDate,
            prayerScore = prayerScore,
            goalCompletionScore = goalScore,
            focusScore = focusScore,
            habitScore = habitScore ?: 0f,
            overallScore = overallScore
        )
        
        dailyScoreDao.insert(score)
    }

    fun observeScoreForDate(date: Long): Flow<DailyScore?> = 
        dailyScoreDao.observeScoreForDate(DateTimeUtil.normalizeToMidnight(date))

    fun observeScoresInRange(startDate: Long, endDate: Long): Flow<List<DailyScore>> =
        dailyScoreDao.observeScoresInRange(DateTimeUtil.normalizeToMidnight(startDate), DateTimeUtil.normalizeToMidnight(endDate))

    suspend fun clearAllScores(): Result<Unit> = safeDbCall {
        dailyScoreDao.deleteAllScores()
    }

}
