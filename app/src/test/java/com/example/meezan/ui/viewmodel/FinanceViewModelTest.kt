package com.example.meezan.ui.viewmodel

import app.cash.turbine.test
import com.example.meezan.data.repository.FinanceRepository
import com.example.meezan.data.repository.UserPreferencesRepository
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
class FinanceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val financeRepository: FinanceRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()

    private lateinit var viewModel: FinanceViewModel

    @Before
    fun setup() {
        coEvery { financeRepository.observeAllTransactions() } returns flowOf(emptyList())
        coEvery { financeRepository.observeActiveLending() } returns flowOf(emptyList())
        coEvery { financeRepository.observeProfile() } returns flowOf(null)
        coEvery { userPreferencesRepository.userPreferencesFlow } returns flowOf(mockk(relaxed = true))
        coEvery { financeRepository.getTodaysSavings() } returns 0.0
        coEvery { financeRepository.addBudget(any(), any()) } returns Result.success(Unit)
        
        viewModel = FinanceViewModel(financeRepository, userPreferencesRepository)
    }

    @Test
    fun `addBudget with negative amount sets error`() = runTest {
        viewModel.addBudget(-10.0, "Test")

        viewModel.snackbarError.test {
            val item = awaitItem()
            if (item == null) {
                assertTrue(awaitItem() is AppError.ValidationError)
            } else {
                assertTrue(item is AppError.ValidationError)
            }
        }
    }

    @Test
    fun `withdraw with zero participants for split sets error`() = runTest {
        viewModel.withdraw(100.0, "Split", isSplit = true, people = emptyList())

        viewModel.snackbarError.test {
            val item = awaitItem()
            if (item == null) {
                assertTrue(awaitItem() is AppError.ValidationError)
            } else {
                assertTrue(item is AppError.ValidationError)
            }
        }
    }
}
