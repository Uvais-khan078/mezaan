package com.example.meezan.ui.viewmodel

import app.cash.turbine.test
import com.example.meezan.data.repository.PrayerTimingRepository
import com.example.meezan.data.repository.UserPreferencesRepository
import com.example.meezan.testutils.MainDispatcherRule
import com.example.meezan.util.AppError
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
class PrayersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val prayerTimingRepository: PrayerTimingRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()

    private lateinit var viewModel: PrayersViewModel

    @Before
    fun setup() {
        coEvery { prayerTimingRepository.observePrayerLogsForDate(any()) } returns flowOf(emptyList())
        coEvery { prayerTimingRepository.observeReminderRules() } returns flowOf(emptyList())
        coEvery { prayerTimingRepository.getPrayerTimingForDate(any()) } returns Result.success(null)
        
        val dummyPrefs = com.example.meezan.data.repository.UserPreferences(
            latitude = 0.0, longitude = 0.0, locationMode = "AUTO", calculationMethod = 2, madhab = 1
        )
        every { userPreferencesRepository.userPreferencesFlow } returns flowOf(dummyPrefs)
        
        viewModel = PrayersViewModel(prayerTimingRepository, userPreferencesRepository)
    }

    @Test
    fun `togglePrayer success updates repository`() = runTest {
        val name = "Fajr"
        val completed = true
        coEvery { prayerTimingRepository.togglePrayer(name, completed) } returns Result.success(Unit)

        viewModel.togglePrayer(name, completed)

        coVerify { prayerTimingRepository.togglePrayer(name, completed) }
    }

    @Test
    fun `fetch timings failure updates error state`() = runTest {
        val error = AppError.NoInternet
        coEvery { 
            prayerTimingRepository.fetchAndSavePrayerTimings(any(), any(), any(), any()) 
        } returns Result.failure(com.example.meezan.util.AppErrorException(error))

        viewModel.fetchPrayerTimingsForLocation(10.0, 20.0, 2)

        viewModel.error.test {
            val item = awaitItem()
            if (item == null) {
                assertEquals(error, awaitItem())
            } else {
                assertEquals(error, item)
            }
        }
    }

    @Test
    fun `saveReminderRule failure updates snackbar error`() = runTest {
        coEvery { 
            prayerTimingRepository.addReminderRule(any()) 
        } returns Result.failure(com.example.meezan.util.AppErrorException(AppError.DatabaseError(Exception())))

        viewModel.saveReminderRule("Fajr", "START", 0, false)

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
