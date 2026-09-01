package com.example.meezan.data.api

import com.example.meezan.data.api.models.AladhanTimingsResponse
import com.example.meezan.data.api.models.AladhanCalendarResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Aladhan API service for fetching prayer timings
 */
interface AladhanApiService {
    @GET("v1/timings/{date}")
    suspend fun getPrayerTimings(
        @Path("date") date: String = "today",
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int = 2, // ISNA by default
        @Query("school") school: Int = 0
    ): AladhanTimingsResponse

    @GET("v1/calendar")
    suspend fun getPrayerCalendar(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int = 2,
        @Query("month") month: Int,
        @Query("year") year: Int,
        @Query("school") school: Int = 1 // Default to Hanafi
    ): AladhanCalendarResponse

    companion object {
        const val BASE_URL = "https://api.aladhan.com/"

        // Prayer calculation methods supported by Aladhan
        enum class PrayerMethod(val id: Int, val displayName: String) {
            ISNA(2, "ISNA"),
            KARACHI(1, "Karachi"),
            MWL(3, "Muslim World League"),
            MAKKAH(4, "Makkah"),
            EGYPT(5, "Egyptian General Authority"),
            CUSTOM(7, "Custom"),
            QATAR(16, "Qatar"),
            SINGAPORE(11, "Singapore"),
            KOREA(15, "Korea"),
            KUWAIT(14, "Kuwait")
        }
    }
}

