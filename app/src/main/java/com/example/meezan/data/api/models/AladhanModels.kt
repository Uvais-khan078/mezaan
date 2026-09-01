package com.example.meezan.data.api.models

import com.squareup.moshi.Json

/**
 * Aladhan API Response models
 */
data class AladhanTimingsResponse(
    val code: Int,
    val status: String,
    val data: AladhanTimingData
)

data class AladhanCalendarResponse(
    val code: Int,
    val status: String,
    val data: List<AladhanTimingData>
)

data class AladhanTimingData(
    val timings: Map<String, String>,
    val date: AladhanDate,
    val meta: AladhanMeta
)

data class AladhanDate(
    val readable: String,
    val timestamp: String,
    val gregorian: AladhanGregorian,
    val hijri: AladhanHijri
)

data class AladhanGregorian(
    val date: String,
    val format: String,
    val day: String,
    val weekday: AladhanWeekday,
    val month: AladhanMonth,
    val year: String
)

data class AladhanWeekday(
    val en: String,
    val ar: String?
)

data class AladhanMonth(
    val number: Int,
    val en: String,
    val ar: String?
)

data class AladhanHijri(
    val date: String,
    val format: String,
    val day: String,
    val weekday: AladhanWeekday,
    val month: AladhanMonth,
    val year: String
)

data class AladhanMeta(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val method: AladhanMethod,
    val school: String,
    val offset: Map<String, Int>
)

data class AladhanMethod(
    val id: Int,
    val name: String
)

