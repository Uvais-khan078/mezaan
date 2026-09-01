package com.example.meezan.data.repository

import com.example.meezan.data.dao.*
import com.example.meezan.data.entities.DailyScore
import com.example.meezan.data.entities.GoalTask
import com.example.meezan.data.entities.TaskStatus
import com.example.meezan.util.DateTimeUtil
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ProductivityScoreRepositoryTest {

    private val prayerLogDao: PrayerLogDao = mockk()
    private val goalTaskDao: GoalTaskDao = mockk()
    private val focusSessionDao: FocusSessionDao = mockk()
    private val habitLogDao: HabitLogDao = mockk()
    private val dailyScoreDao: DailyScoreDao = mockk()

    private lateinit var repository: ProductivityScoreRepository

    @Before
    fun setup() {
        repository = ProductivityScoreRepository(
            prayerLogDao,
            goalTaskDao,
            focusSessionDao,
            habitLogDao,
            dailyScoreDao
        )
    }

    @Test
    fun `calculateAndSaveDailyScore with zero active pillars returns 0 score`() = runTest {
        val date = 12345L
        val normalizedDate = DateTimeUtil.normalizeToMidnight(date)

        coEvery { prayerLogDao.getLogsForDate(normalizedDate) } returns emptyList()
        coEvery { goalTaskDao.getTasksForDate(normalizedDate) } returns emptyList()
        coEvery { habitLogDao.observeLogsForDate(normalizedDate) } returns flowOf(emptyList())
        coEvery { dailyScoreDao.insert(any()) } just Runs

        repository.calculateAndSaveDailyScore(date)

        coVerify {
            dailyScoreDao.insert(match { 
                it.overallScore == 0f && it.prayerScore == 0f && it.goalCompletionScore == 100f
            })
        }
    }

    @Test
    fun `calculateAndSaveDailyScore with full completion returns 100 score`() = runTest {
        val date = 12345L
        val normalizedDate = DateTimeUtil.normalizeToMidnight(date)

        // All 5 prayers done
        coEvery { prayerLogDao.getLogsForDate(normalizedDate) } returns List(5) { mockk(relaxed = true) { every { completed } returns true } }
        
        // 1 Goal task done
        val task = mockk<GoalTask>(relaxed = true) {
            every { id } returns 1L
            every { status } returns TaskStatus.COMPLETED
            every { requiredDurationMinutes } returns 60
        }
        coEvery { goalTaskDao.getTasksForDate(normalizedDate) } returns listOf(task)
        
        // Focus done
        coEvery { focusSessionDao.getTotalDurationsForTasks(listOf(1L)) } returns listOf(TaskFocusDuration(1L, 60))
        
        // Habit done
        coEvery { habitLogDao.observeLogsForDate(normalizedDate) } returns flowOf(listOf(mockk(relaxed = true) { every { completed } returns true }))
        
        coEvery { dailyScoreDao.insert(any()) } just Runs

        repository.calculateAndSaveDailyScore(date)

        coVerify {
            dailyScoreDao.insert(match { 
                it.overallScore == 100f && it.prayerScore == 100f && it.focusScore == 100f
            })
        }
    }

    @Test
    fun `calculateAndSaveDailyScore handles division by zero for tasks`() = runTest {
        val date = 12345L
        val normalizedDate = DateTimeUtil.normalizeToMidnight(date)

        coEvery { prayerLogDao.getLogsForDate(normalizedDate) } returns emptyList()
        coEvery { goalTaskDao.getTasksForDate(normalizedDate) } returns emptyList() // 0 tasks
        coEvery { habitLogDao.observeLogsForDate(normalizedDate) } returns flowOf(emptyList()) // 0 habits
        coEvery { dailyScoreDao.insert(any()) } just Runs

        repository.calculateAndSaveDailyScore(date)

        coVerify {
            dailyScoreDao.insert(match { 
                it.goalCompletionScore == 100f && it.focusScore == 100f && it.habitScore == 0f
            })
        }
    }
}
