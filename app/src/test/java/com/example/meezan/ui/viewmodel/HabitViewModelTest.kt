package com.example.meezan.ui.viewmodel

import app.cash.turbine.test
import com.example.meezan.data.repository.HabitRepository
import com.example.meezan.data.repository.ProductivityScoreRepository
import com.example.meezan.testutils.MainDispatcherRule
import com.example.meezan.util.AppError
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class HabitViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val habitRepository: HabitRepository = mockk()
    private val scoreRepository: ProductivityScoreRepository = mockk()

    private lateinit var viewModel: HabitViewModel

    @Before
    fun setup() {
        coEvery { habitRepository.observeActiveHabits() } returns flowOf(emptyList())
        coEvery { habitRepository.getHabitsWithHistory(any()) } returns emptyList()
        coEvery { habitRepository.observeLogsForDate(any()) } returns flowOf(emptyList())
        
        viewModel = HabitViewModel(habitRepository, scoreRepository)
    }

    @Test
    fun `toggleHabit failure sets snackbar error`() = runTest {
        coEvery { 
            habitRepository.toggleHabit(any(), any(), any()) 
        } returns Result.failure(com.example.meezan.util.AppErrorException(AppError.DatabaseError(Exception("DB Fail"))))

        viewModel.toggleHabit(1L, true)

        viewModel.snackbarError.test {
            val item = awaitItem()
            if (item == null) {
                assertTrue(awaitItem() is AppError.DatabaseError)
            } else {
                assertTrue(item is AppError.DatabaseError)
            }
        }
    }
}
