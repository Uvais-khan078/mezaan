package com.example.meezan.data.repository

import com.example.meezan.util.AppError
import com.example.meezan.util.AppErrorException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class BaseRepositoryTest {

    private val repository = object : BaseRepository() {}

    @Test
    fun `safeApiCall maps IOException to NoInternet`() = runTest {
        val result = repository.safeApiCall {
            throw IOException("Network fail")
        }
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as AppErrorException
        assertTrue(exception.error is AppError.NoInternet)
    }

    @Test
    fun `safeApiCall maps HttpException to ApiError`() = runTest {
        val response = Response.error<Any>(404, "".toResponseBody(null))
        val result = repository.safeApiCall {
            throw HttpException(response)
        }
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as AppErrorException
        val error = exception.error as AppError.ApiError
        assertTrue(error.code == 404)
    }

    @Test
    fun `safeDbCall maps generic exception to DatabaseError`() = runTest {
        val result = repository.safeDbCall {
            throw RuntimeException("DB fail")
        }
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as AppErrorException
        assertTrue(exception.error is AppError.DatabaseError)
    }
}
