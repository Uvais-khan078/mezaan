package com.example.meezan.data.repository

import com.example.meezan.data.dao.FocusSessionDao
import com.example.meezan.data.dao.GoalTaskDao
import com.example.meezan.data.entities.FocusSession
import com.example.meezan.data.entities.GoalTask

class FocusRepository(
    private val taskDao: GoalTaskDao,
    private val sessionDao: FocusSessionDao
) : BaseRepository() {
    suspend fun getTaskById(taskId: Long): Result<GoalTask?> = safeDbCall {
        taskDao.getTaskById(taskId)
    }

    suspend fun insertSession(session: FocusSession): Result<Unit> = safeDbCall {
        sessionDao.insert(session)
    }

    suspend fun updateTask(task: GoalTask): Result<Unit> = safeDbCall {
        taskDao.update(task)
    }

    suspend fun getTotalFocusDuration(taskId: Long): Result<Int> = safeDbCall {
        sessionDao.getTotalDurationForTask(taskId) ?: 0
    }
}
