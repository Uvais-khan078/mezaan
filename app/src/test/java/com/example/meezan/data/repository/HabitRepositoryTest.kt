package com.example.meezan.data.repository

import com.example.meezan.data.dao.HabitDao
import com.example.meezan.data.dao.HabitLogDao
import com.example.meezan.data.database.MeezanDatabase
import com.example.meezan.data.entities.Habit
import com.example.meezan.data.entities.HabitLog
import com.example.meezan.data.scheduler.ReminderScheduler
import com.example.meezan.util.DateTimeUtil
import io.mockk.*
import androidx.room.withTransaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class HabitRepositoryTest {

    private val database: MeezanDatabase = mockk()
    private val habitDao: HabitDao = mockk()
    private val habitLogDao: HabitLogDao = mockk()
    private val reminderScheduler: ReminderScheduler = mockk()

    private lateinit var repository: HabitRepository

    @Before
    fun setup() {
        val slot = slot<suspend () -> Any>()
        coEvery { database.withTransaction<Any>(capture(slot)) } coAnswers { slot.captured.invoke() }

        repository = HabitRepository(
            database,
            habitDao,
            habitLogDao,
            reminderScheduler
        )
    }

    @Test
    fun `getStreak returns correct count for consecutive days`() = runTest {
        val habitId = 1L
        val today = DateTimeUtil.getTodayAtMidnight()
        val yesterday = today - 24 * 60 * 60 * 1000L
        val dayBefore = yesterday - 24 * 60 * 60 * 1000L

        // Today done, yesterday done, day before not done
        coEvery { habitLogDao.isHabitCompletedForDate(habitId, today) } returns 1
        coEvery { habitLogDao.isHabitCompletedForDate(habitId, yesterday) } returns 1
        coEvery { habitLogDao.isHabitCompletedForDate(habitId, dayBefore) } returns 0

        val streak = repository.getStreak(habitId)

        assertEquals(2, streak)
    }

    @Test
    fun `getStreak returns 0 if neither today nor yesterday is done`() = runTest {
        val habitId = 1L
        val today = DateTimeUtil.getTodayAtMidnight()
        val yesterday = today - 24 * 60 * 60 * 1000L

        coEvery { habitLogDao.isHabitCompletedForDate(habitId, today) } returns 0
        coEvery { habitLogDao.isHabitCompletedForDate(habitId, yesterday) } returns 0

        val streak = repository.getStreak(habitId)

        assertEquals(0, streak)
    }

    @Test
    fun `getStreak handles gap in the middle`() = runTest {
        val habitId = 1L
        val today = DateTimeUtil.getTodayAtMidnight()
        val yesterday = today - 86400000L
        val dayBefore = yesterday - 86400000L

        coEvery { habitLogDao.isHabitCompletedForDate(habitId, today) } returns 1
        coEvery { habitLogDao.isHabitCompletedForDate(habitId, yesterday) } returns 0 // Gap
        coEvery { habitLogDao.isHabitCompletedForDate(habitId, dayBefore) } returns 1

        val streak = repository.getStreak(habitId)

        assertEquals(1, streak) // Streak broken by yesterday
    }
}
