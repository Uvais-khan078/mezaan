package com.example.meezan.data.repository

import com.example.meezan.data.dao.*
import com.example.meezan.data.database.MeezanDatabase
import com.example.meezan.data.entities.*
import io.mockk.*
import androidx.room.withTransaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class FinanceRepositoryTest {

    private val database: MeezanDatabase = mockk()
    private val transactionDao: FinanceTransactionDao = mockk()
    private val lendingDao: LendingDao = mockk()
    private val profileDao: FinanceProfileDao = mockk()

    private lateinit var repository: FinanceRepository

    @Before
    fun setup() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        // Mock Room transaction
        val slot = slot<suspend () -> Any>()
        coEvery { any<MeezanDatabase>().withTransaction<Any>(capture(slot)) } coAnswers { slot.captured.invoke() }

        repository = FinanceRepository(
            database,
            transactionDao,
            lendingDao,
            profileDao
        )
    }

    @Test
    fun `recalculateDailyLimit distributes budget over remaining days`() = runTest {
        val cal = Calendar.getInstance()
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        
        val profile = FinanceProfile(
            totalMonthlyBudget = 2000.0,
            lastUpdateDate = cal.timeInMillis
        )
        
        coEvery { profileDao.updateProfile(any()) } just Runs

        repository.recalculateDailyLimit(profile)

        val daysRemaining = (daysInMonth - currentDay).coerceAtLeast(1)
        val expectedLimit = 2000.0 / daysRemaining
        
        coVerify {
            profileDao.updateProfile(match { 
                Math.abs(it.dailyLimit - expectedLimit) < 0.1
            })
        }
    }

    @Test
    fun `recalculateDailyLimit on month change rolls budget to savings`() = runTest {
        val lastMonthCal = Calendar.getInstance().apply { 
            add(Calendar.MONTH, -1)
        }
        
        val profile = FinanceProfile(
            totalMonthlyBudget = 500.0,
            totalSavings = 1000.0,
            lastUpdateDate = lastMonthCal.timeInMillis
        )
        
        coEvery { transactionDao.insert(any()) } returns 1L
        coEvery { profileDao.updateProfile(any()) } just Runs

        repository.recalculateDailyLimit(profile)

        coVerify {
            // Check rollover transaction
            transactionDao.insert(match { it.type == TransactionType.SAVINGS_DEPOSIT && it.amount == 500.0 })
            // Check profile update: savings increased, budget reset to 0 before daily limit calc
            profileDao.updateProfile(match { it.totalSavings == 1500.0 && it.totalMonthlyBudget == 0.0 })
        }
    }
}
