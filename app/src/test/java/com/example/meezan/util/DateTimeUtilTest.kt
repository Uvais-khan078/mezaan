package com.example.meezan.util

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class DateTimeUtilTest {

    @Test
    fun `formatTimeFromMillis correctly formats time`() {
        // 14:30 in millis since midnight
        val millis = (14 * 60 + 30) * 60 * 1000L
        assertEquals("14:30", DateTimeUtil.formatTimeFromMillis(millis))
    }

    @Test
    fun `formatDuration handles various ranges`() {
        assertEquals("1h 30m", DateTimeUtil.formatDuration((90 * 60 * 1000L)))
        assertEquals("45m", DateTimeUtil.formatDuration((45 * 60 * 1000L)))
        assertEquals("0m", DateTimeUtil.formatDuration(0L))
    }

    @Test
    fun `normalizeToMidnight resets time components`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 1, 15, 30, 45)
            set(Calendar.MILLISECOND, 500)
        }
        val normalized = DateTimeUtil.normalizeToMidnight(cal.timeInMillis)
        
        val resultCal = Calendar.getInstance().apply { timeInMillis = normalized }
        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, resultCal.get(Calendar.MONTH))
        assertEquals(1, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))
        assertEquals(0, resultCal.get(Calendar.SECOND))
        assertEquals(0, resultCal.get(Calendar.MILLISECOND))
    }
}
