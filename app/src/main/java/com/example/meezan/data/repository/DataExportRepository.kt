package com.example.meezan.data.repository

import com.example.meezan.data.dao.*
import com.example.meezan.data.entities.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataExportRepository(
    private val goalDao: GoalDao,
    private val goalTaskDao: GoalTaskDao,
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
    private val focusSessionDao: FocusSessionDao,
    private val moshi: Moshi
) : BaseRepository() {
    suspend fun exportDataToJson(): Result<String> = safeDbCall {
        val backup = MeezanBackup(
            goals = goalDao.getAllGoals(),
            tasks = goalTaskDao.getAllTasks(),
            habits = habitDao.getAllHabits(),
            habitLogs = habitLogDao.getAllHabitLogs(),
            focusSessions = focusSessionDao.getAllSessions()
        )
        val adapter = moshi.adapter(MeezanBackup::class.java)
        adapter.toJson(backup)
    }

    suspend fun importDataFromJson(json: String): Result<Unit> = safeDbCall {
        val adapter = moshi.adapter(MeezanBackup::class.java)
        val backup = adapter.fromJson(json) ?: throw Exception("Invalid backup format")
        
        // Simple import: insert everything. 
        backup.goals.forEach { goalDao.insert(it) }
        backup.tasks.forEach { goalTaskDao.insert(it) }
        backup.habits.forEach { habitDao.insert(it) }
        backup.habitLogs.forEach { habitLogDao.insert(it) }
        backup.focusSessions.forEach { focusSessionDao.insert(it) }
    }
}

data class MeezanBackup(
    val goals: List<Goal>,
    val tasks: List<GoalTask>,
    val habits: List<Habit>,
    val habitLogs: List<HabitLog>,
    val focusSessions: List<FocusSession>
)
