package com.example.meezan.data.repository

import com.example.meezan.data.dao.GoalDao
import com.example.meezan.data.dao.GoalTaskDao
import com.example.meezan.data.database.MeezanDatabase
import com.example.meezan.data.entities.Goal
import com.example.meezan.data.entities.GoalTask
import com.example.meezan.data.entities.TaskStatus
import com.example.meezan.data.scheduler.ReminderScheduler
import com.example.meezan.domain.parser.ParsedRoadmap
import com.example.meezan.util.Constants
import com.example.meezan.util.DateTimeUtil
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

class GoalRepository(
    private val database: MeezanDatabase,
    private val goalDao: GoalDao,
    private val goalTaskDao: GoalTaskDao,
    private val reminderScheduler: ReminderScheduler
) : BaseRepository() {

    fun observeTasksForDate(date: Long): Flow<List<GoalTask>> = 
        goalTaskDao.observeTasksForDate(DateTimeUtil.normalizeToMidnight(date))

    suspend fun getTasksForDate(date: Long): Result<List<GoalTask>> = safeDbCall {
        goalTaskDao.getTasksForDate(DateTimeUtil.normalizeToMidnight(date))
    }

    suspend fun updateTaskStatus(task: GoalTask, status: TaskStatus): Result<Unit> = safeDbCall {
        goalTaskDao.update(task.copy(status = status))
    }

    suspend fun setTaskReminder(task: GoalTask, hour: Int, minute: Int): Result<Unit> = safeDbCall {
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = task.taskDate
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val reminderTime = calendar.timeInMillis
        val updated = task.copy(reminderTime = reminderTime)
        goalTaskDao.update(updated)
        reminderScheduler.scheduleReminder(
            reminderTime = reminderTime,
            reminderId = task.id.toInt(),
            reminderType = ReminderScheduler.ReminderType.TASK
        )
    }

    suspend fun getNextUpcomingTask(date: Long): Result<GoalTask?> = safeDbCall {
        goalTaskDao.getNextUpcomingTask(date)
    }

    suspend fun seedDefaultTaskReminders(tasks: List<GoalTask>): Result<Unit> = safeDbCall {
        val tasksToSeed = tasks.filter { it.reminderTime == null }
        if (tasksToSeed.isNotEmpty()) {
            tasksToSeed.forEach { task ->
                val calendar = java.util.Calendar.getInstance().apply {
                    timeInMillis = task.taskDate
                    set(java.util.Calendar.HOUR_OF_DAY, Constants.Reminders.DEFAULT_TASK_REMINDER_HOUR)
                    set(java.util.Calendar.MINUTE, Constants.Reminders.DEFAULT_TASK_REMINDER_MINUTE)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val defaultReminderTime = calendar.timeInMillis
                val updated = task.copy(reminderTime = defaultReminderTime)
                goalTaskDao.update(updated)
                if (defaultReminderTime > System.currentTimeMillis()) {
                    reminderScheduler.scheduleReminder(
                        reminderTime = defaultReminderTime,
                        reminderId = task.id.toInt(),
                        reminderType = ReminderScheduler.ReminderType.TASK
                    )
                }
            }
        }
    }
    suspend fun saveParsedRoadmap(roadmap: ParsedRoadmap): Result<Long> = safeDbCall {
        val startLocalDate = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        
        database.withTransaction {
            val goalId = goalDao.insert(
                Goal(
                    title = roadmap.goalTitle,
                    createdDate = startLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    isActive = true
                )
            )

            val tasks = roadmap.entries.map { entry ->
                val taskDate = startLocalDate.plusDays((entry.dayNumber - 1).toLong())
                GoalTask(
                    goalId = goalId,
                    dayNumber = entry.dayNumber,
                    taskName = entry.taskName,
                    requiredDurationMinutes = entry.durationMinutes,
                    taskDate = taskDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    reminderTime = null
                )
            }
            goalTaskDao.insertAll(tasks)
            goalId
        }
    }

    suspend fun deleteGoal(goalId: Long): Result<Unit> = safeDbCall {
        database.withTransaction {
            goalDao.getGoalById(goalId)?.let { goal ->
                val tasks = goalTaskDao.getTasksForGoal(goalId)
                tasks.forEach { task ->
                    reminderScheduler.cancelReminder(task.id.toInt(), ReminderScheduler.ReminderType.TASK)
                }
                goalTaskDao.deleteTasksByGoalId(goalId)
                goalDao.delete(goal)
            }
        }
    }

    fun observeActiveGoals() = goalDao.observeActiveGoals()
}
