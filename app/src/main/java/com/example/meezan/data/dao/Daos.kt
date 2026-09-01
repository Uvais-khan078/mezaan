package com.example.meezan.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.example.meezan.data.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerTimingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prayerTiming: PrayerTiming)

    @Update
    suspend fun update(prayerTiming: PrayerTiming)

    @Query("SELECT * FROM prayer_timings WHERE date = :date LIMIT 1")
    suspend fun getPrayerTimingForDate(date: Long): PrayerTiming?

    @Query("SELECT * FROM prayer_timings ORDER BY date DESC LIMIT 1")
    suspend fun getLatestPrayerTiming(): PrayerTiming?

    @Query("SELECT * FROM prayer_timings WHERE date = :date")
    fun observePrayerTimingForDate(date: Long): Flow<PrayerTiming?>
}

@Dao
interface PrayerLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: PrayerLog)

    @Update
    suspend fun update(log: PrayerLog)

    @Query("SELECT * FROM prayer_logs WHERE date = :date")
    suspend fun getLogsForDate(date: Long): List<PrayerLog>

    @Query("SELECT * FROM prayer_logs WHERE date = :date")
    fun observeLogsForDate(date: Long): Flow<List<PrayerLog>>

    @Query("DELETE FROM prayer_logs")
    suspend fun deleteAllLogs()
}

@Dao
interface PrayerReminderRuleDao {
    @Insert
    suspend fun insert(rule: PrayerReminderRule)

    @Update
    suspend fun update(rule: PrayerReminderRule)

    @Delete
    suspend fun delete(rule: PrayerReminderRule)

    @Query("SELECT * FROM prayer_reminder_rules WHERE enabled = 1")
    suspend fun getEnabledRules(): List<PrayerReminderRule>

    @Query("SELECT * FROM prayer_reminder_rules")
    fun observeAllRules(): Flow<List<PrayerReminderRule>>

    @Query("SELECT * FROM prayer_reminder_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): PrayerReminderRule?
}

@Dao
interface GoalDao {
    @Insert
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goal: Goal)

    @Delete
    suspend fun delete(goal: Goal)

    @Query("SELECT * FROM goals WHERE isActive = 1 ORDER BY createdDate DESC")
    fun observeActiveGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE id = :goalId")
    suspend fun getGoalById(goalId: Long): Goal?

    @Query("SELECT * FROM goals")
    suspend fun getAllGoals(): List<Goal>

    @Query("DELETE FROM goals")
    suspend fun deleteAllGoals()
}

@Dao
interface GoalTaskDao {
    @Insert
    suspend fun insert(task: GoalTask)

    @Insert
    suspend fun insertAll(tasks: List<GoalTask>)

    @Update
    suspend fun update(task: GoalTask)

    @Delete
    suspend fun delete(task: GoalTask)

    @Query("SELECT * FROM goal_tasks WHERE taskDate = :date ORDER BY dayNumber ASC")
    fun observeTasksForDate(date: Long): Flow<List<GoalTask>>

    @Query("SELECT * FROM goal_tasks WHERE taskDate = :date ORDER BY dayNumber ASC")
    suspend fun getTasksForDate(date: Long): List<GoalTask>

    @Query("SELECT * FROM goal_tasks WHERE goalId = :goalId ORDER BY dayNumber ASC")
    suspend fun getTasksForGoal(goalId: Long): List<GoalTask>

    @Query("SELECT * FROM goal_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Long): GoalTask?

    @Query("SELECT COUNT(*) FROM goal_tasks WHERE taskDate = :date AND status = 'COMPLETED'")
    suspend fun getCompletedTaskCountForDate(date: Long): Int

    @Query("SELECT COUNT(*) FROM goal_tasks WHERE taskDate = :date")
    suspend fun getTotalTaskCountForDate(date: Long): Int

    @Query("DELETE FROM goal_tasks WHERE goalId = :goalId")
    suspend fun deleteTasksByGoalId(goalId: Long)

    @Query("SELECT * FROM goal_tasks")
    suspend fun getAllTasks(): List<GoalTask>

    @Query("SELECT * FROM goal_tasks WHERE taskDate > :date AND status != 'COMPLETED' ORDER BY taskDate ASC LIMIT 1")
    suspend fun getNextUpcomingTask(date: Long): GoalTask?

    @Query("DELETE FROM goal_tasks")
    suspend fun deleteAllTasks()
}

@Dao
interface FocusSessionDao {
    @Insert
    suspend fun insert(session: FocusSession)

    @Update
    suspend fun update(session: FocusSession)

    @Query("SELECT * FROM focus_sessions WHERE taskId = :taskId")
    suspend fun getSessionsForTask(taskId: Long): List<FocusSession>

    @Query("SELECT SUM(actualDurationMinutes) FROM focus_sessions WHERE taskId = :taskId")
    suspend fun getTotalDurationForTask(taskId: Long): Int?

    @Query("SELECT * FROM focus_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): FocusSession?

    @Query("SELECT * FROM focus_sessions")
    suspend fun getAllSessions(): List<FocusSession>

    @Query("SELECT taskId, SUM(actualDurationMinutes) as duration FROM focus_sessions WHERE taskId IN (:taskIds) GROUP BY taskId")
    suspend fun getTotalDurationsForTasks(taskIds: List<Long>): List<TaskFocusDuration>

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAllSessions()
}

data class TaskFocusDuration(
    val taskId: Long,
    val duration: Int
)

@Dao
interface HabitDao {
    @Insert
    suspend fun insert(habit: Habit)

    @Update
    suspend fun update(habit: Habit)

    @Delete
    suspend fun delete(habit: Habit)

    @Query("SELECT * FROM habits WHERE isActive = 1 ORDER BY createdDate DESC")
    fun observeActiveHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :habitId")
    suspend fun getHabitById(habitId: Long): Habit?

    @Query("SELECT * FROM habits")
    suspend fun getAllHabits(): List<Habit>

    @Query("DELETE FROM habits")
    suspend fun deleteAllHabits()
}

@Dao
interface HabitLogDao {
    @Insert
    suspend fun insert(log: HabitLog)

    @Update
    suspend fun update(log: HabitLog)

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND date = :date")
    suspend fun getLogForHabitDate(habitId: Long, date: Long): HabitLog?

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND date = :date")
    fun observeLogForHabitDate(habitId: Long, date: Long): Flow<HabitLog?>

    @Query("SELECT * FROM habit_logs WHERE date = :date ORDER BY habitId ASC")
    fun observeLogsForDate(date: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY date DESC LIMIT 7")
    suspend fun getLast7LogsForHabit(habitId: Long): List<HabitLog>

    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND date = :date AND completed = 1")
    suspend fun isHabitCompletedForDate(habitId: Long, date: Long): Int

    @Query("SELECT * FROM habit_logs WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun observeLogsInRange(startDate: Long, endDate: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs")
    suspend fun getAllHabitLogs(): List<HabitLog>

    @Query("SELECT * FROM habit_logs WHERE habitId IN (:habitIds) AND date >= :startDate ORDER BY date ASC")
    suspend fun getLogsForHabits(habitIds: List<Long>, startDate: Long): List<HabitLog>

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteLogsByHabitId(habitId: Long)

    @Query("DELETE FROM habit_logs")
    suspend fun deleteAllLogs()
}

@Dao
interface DailyScoreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(score: DailyScore)

    @Update
    suspend fun update(score: DailyScore)

    @Query("SELECT * FROM daily_scores WHERE date = :date")
    suspend fun getScoreForDate(date: Long): DailyScore?

    @Query("SELECT * FROM daily_scores WHERE date = :date")
    fun observeScoreForDate(date: Long): Flow<DailyScore?>

    @Query("SELECT * FROM daily_scores WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun observeScoresInRange(startDate: Long, endDate: Long): Flow<List<DailyScore>>

    @Query("DELETE FROM daily_scores")
    suspend fun deleteAllScores()
}

@Dao
interface FinanceTransactionDao {
    @Insert
    suspend fun insert(transaction: FinanceTransaction): Long

    @Update
    suspend fun update(transaction: FinanceTransaction)

    @Delete
    suspend fun delete(transaction: FinanceTransaction)

    @Query("SELECT * FROM finance_transactions ORDER BY date DESC")
    fun observeAllTransactions(): Flow<List<FinanceTransaction>>

    @Query("SELECT * FROM finance_transactions")
    suspend fun getAllTransactions(): List<FinanceTransaction>

    @Query("SELECT * FROM finance_transactions WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun observeTransactionsInRange(startDate: Long, endDate: Long): Flow<List<FinanceTransaction>>

    @Query("SELECT SUM(amount) FROM finance_transactions WHERE (type = 'WITHDRAWAL' OR type = 'SAVINGS_WITHDRAWAL') AND date >= :startDate AND date <= :endDate")
    suspend fun getTotalWithdrawalsInRange(startDate: Long, endDate: Long): Double?

    @Query("DELETE FROM finance_transactions")
    suspend fun deleteAllTransactions()
}

@Dao
interface LendingDao {
    @Insert
    suspend fun insert(record: LendingRecord): Long

    @Update
    suspend fun update(record: LendingRecord)

    @Query("SELECT * FROM lending_records WHERE isCleared = 0 ORDER BY date DESC")
    fun observeActiveLending(): Flow<List<LendingRecord>>

    @Query("SELECT * FROM lending_records WHERE id = :id")
    suspend fun getRecordById(id: Long): LendingRecord?

    @Query("SELECT * FROM lending_records")
    suspend fun getAllLendingRecords(): List<LendingRecord>

    @Query("DELETE FROM lending_records")
    suspend fun deleteAllRecords()
}

@Dao
interface FinanceProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateProfile(profile: FinanceProfile)

    @Query("SELECT * FROM finance_profile WHERE id = 1")
    suspend fun getProfile(): FinanceProfile?

    @Query("SELECT * FROM finance_profile WHERE id = 1")
    fun observeProfile(): Flow<FinanceProfile?>

    @Query("DELETE FROM finance_profile")
    suspend fun deleteProfile()
}

