package com.example.meezan.data.repository

import com.example.meezan.data.api.AladhanApiService
import com.example.meezan.data.dao.PrayerLogDao
import com.example.meezan.data.dao.PrayerReminderRuleDao
import com.example.meezan.data.dao.PrayerTimingDao
import com.example.meezan.data.scheduler.ReminderScheduler
import com.example.meezan.util.AppError
import com.example.meezan.util.AppErrorException
import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class PrayerTimingRepositoryTest {

    private val timingDao: PrayerTimingDao = mockk()
    private val logDao: PrayerLogDao = mockk()
    private val ruleDao: PrayerReminderRuleDao = mockk()
    private val scheduler: ReminderScheduler = mockk()
    private val apiService: AladhanApiService = mockk()

    private lateinit var repository: PrayerTimingRepository

    @Before
    fun setup() {
        repository = PrayerTimingRepository(
            timingDao,
            logDao,
            ruleDao,
            scheduler,
            apiService
        )
    }

    @Test
    fun `fetchAndSavePrayerTimings maps IOException to NoInternet`() = runTest {
        coEvery { 
            apiService.getPrayerCalendar(any(), any(), any(), any(), any(), any()) 
        } throws IOException()

        val result = repository.fetchAndSavePrayerTimings(0.0, 0.0)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as AppErrorException).error
        assertTrue(error is AppError.NoInternet)
    }

    @Test
    fun `fetchAndSavePrayerTimings maps 500 error to ApiError`() = runTest {
        val response = Response.error<Any>(500, "".toResponseBody(null))
        coEvery { 
            apiService.getPrayerCalendar(any(), any(), any(), any(), any(), any()) 
        } throws HttpException(response)

        val result = repository.fetchAndSavePrayerTimings(0.0, 0.0)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as AppErrorException).error
        assertTrue(error is AppError.ApiError && error.code == 500)
    }
}
