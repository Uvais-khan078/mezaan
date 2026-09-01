package com.example.meezan.util

import com.example.meezan.R
import org.junit.Assert.assertEquals
import org.junit.Test

class AppErrorTest {

    @Test
    fun `getMessageResId returns correct resource for NoInternet`() {
        assertEquals(R.string.error_no_internet, AppError.NoInternet.getMessageResId())
    }

    @Test
    fun `getMessageResId returns correct resource for ApiError`() {
        assertEquals(R.string.error_api_failure, AppError.ApiError(500, "Server Error").getMessageResId())
    }

    @Test
    fun `getMessageResId returns correct resource for DatabaseError`() {
        assertEquals(R.string.error_database_failure, AppError.DatabaseError(Exception()).getMessageResId())
    }
}
