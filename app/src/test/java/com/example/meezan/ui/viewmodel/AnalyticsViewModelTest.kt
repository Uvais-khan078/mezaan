package com.example.meezan.ui.viewmodel

import app.cash.turbine.test
import com.example.meezan.data.entities.DailyScore
import com.example.meezan.data.repository.FinanceRepository
import com.example.meezan.data.repository.HabitRepository
import com.example.meezan.data.repository.ProductivityScoreRepository
import com.example.meezan.testutils.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class AnalyticsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scoreRepository: ProductivityScoreRepository = mockk()
    private val financeRepository: FinanceRepository = mockk()
    private val habitRepository: HabitRepository = mockk()

    private lateinit var viewModel: AnalyticsViewModel

    @Before
    fun setup() {
        coEvery { scoreRepository.observeScoresInRange(any(), any()) } returns flowOf(emptyList())
        coEvery { financeRepository.observeTransactionsInRange(any(), any()) } returns flowOf(emptyList())
        coEvery { habitRepository.observeLogsInRange(any(), any()) } returns flowOf(emptyList())
        
        viewModel = AnalyticsViewModel(scoreRepository, financeRepository, habitRepository)
    }

    @Test
    fun `initial state has zero average score`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(0f, state.averageScore)
        }
    }

    @Test
    fun `state updates correctly when score data is available`() = runTest {
        val scores = listOf(
            DailyScore(date = 1L, overallScore = 80f),
            DailyScore(date = 2L, overallScore = 100f)
        )
        coEvery { scoreRepository.observeScoresInRange(any(), any()) } returns flowOf(scores)
        
        // Re-initialize to trigger trends calculation
        val newVm = AnalyticsViewModel(scoreRepository, financeRepository, habitRepository)
        
        newVm.uiState.test {
            val state = awaitItem()
            assertEquals(90f, state.averageScore)
        }
    }
}
