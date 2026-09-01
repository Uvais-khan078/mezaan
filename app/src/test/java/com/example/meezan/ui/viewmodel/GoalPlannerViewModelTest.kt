package com.example.meezan.ui.viewmodel

import app.cash.turbine.test
import com.example.meezan.data.repository.GoalRepository
import com.example.meezan.testutils.MainDispatcherRule
import com.example.meezan.util.AppError
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class GoalPlannerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val goalRepository: GoalRepository = mockk()

    private lateinit var viewModel: GoalPlannerViewModel

    @Before
    fun setup() {
        coEvery { goalRepository.observeTasksForDate(any()) } returns flowOf(emptyList())
        coEvery { goalRepository.observeActiveGoals() } returns flowOf(emptyList())
        coEvery { goalRepository.getNextUpcomingTask(any()) } returns Result.success(null)
        
        viewModel = GoalPlannerViewModel(goalRepository)
    }

    @Test
    fun `parseRoadmap with valid text updates uiState`() = runTest {
        val input = "Goal: Study\nDay 1\nTask: Math\nDuration: 1h"
        viewModel.onInputChange(input)
        
        viewModel.parseRoadmap()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Study", state.parsedRoadmap?.goalTitle)
            assertNull(state.error)
        }
    }

    @Test
    fun `parseRoadmap with invalid text sets error state`() = runTest {
        val input = "Goal: Study\nDay 1\nTask: Math" // missing duration
        viewModel.onInputChange(input)
        
        viewModel.parseRoadmap()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.error is AppError.RoadmapParseError)
        }
    }
}
