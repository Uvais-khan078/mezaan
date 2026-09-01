package com.example.meezan.ui.viewmodel

import android.content.Context
import app.cash.turbine.test
import com.example.meezan.data.repository.FocusRepository
import com.example.meezan.testutils.MainDispatcherRule
import com.example.meezan.util.AppError
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class FocusTimerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = mockk(relaxed = true)
    private val focusRepository: FocusRepository = mockk()

    private lateinit var viewModel: FocusTimerViewModel

    @Before
    fun setup() {
        coEvery { focusRepository.getTaskById(any()) } returns Result.success(null)
        // Service binding is hard to test in unit test without Robolectric, 
        // but we can test basic logic.
        
        viewModel = FocusTimerViewModel(context, focusRepository, 1L)
    }

    @Test
    fun `loadTask failure sets error state`() = runTest {
        coEvery { 
            focusRepository.getTaskById(1L) 
        } returns Result.failure(com.example.meezan.util.AppErrorException(AppError.DatabaseError(Exception("DB Fail"))))
        
        // Re-initialize to trigger init block again or just call loadTask (it's private)
        // Actually, init block already ran in setup. 
        // We'll just manually call the same logic if possible or test another flow.
        
        // Let's just create a new one with the failing mock
        val failingVm = FocusTimerViewModel(context, focusRepository, 1L)
        
        failingVm.error.test {
            val item = awaitItem()
            if (item == null) {
                assertTrue(awaitItem() is AppError.DatabaseError)
            } else {
                assertTrue(item is AppError.DatabaseError)
            }
        }
    }
}
