package com.example.meezan.data.repository

import com.example.meezan.util.AppError
import com.example.meezan.util.AppErrorException
import retrofit2.HttpException
import java.io.IOException

/**
 * Base Repository providing safe wrappers for API and Database calls.
 */
abstract class BaseRepository {

    suspend fun <T> safeApiCall(call: suspend () -> T): Result<T> {
        return try {
            Result.success(call())
        } catch (_: IOException) {
            Result.failure(AppErrorException(AppError.NoInternet))
        } catch (e: HttpException) {
            Result.failure(AppErrorException(AppError.ApiError(e.code(), e.message())))
        } catch (e: Exception) {
            Result.failure(AppErrorException(AppError.UnknownError(e)))
        }
    }

    suspend fun <T> safeDbCall(call: suspend () -> T): Result<T> {
        return try {
            Result.success(call())
        } catch (e: AppErrorException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(AppErrorException(AppError.DatabaseError(e)))
        }
    }
}
