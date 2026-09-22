package com.lagradost.cloudstream3.mvvm

import com.lagradost.api.Log

sealed class Resource<out T> {
    data class Success<out T>(val value: T) : Resource<T>()
    data class Failure(
        val isNetworkError: Boolean,
        val errorString: String,
    ) : Resource<Nothing>()

    data class Loading(val url: String? = null) : Resource<Nothing>()

    companion object {
        fun <T> fromResult(result: Result<T>): Resource<T> {
            val value = result.getOrNull()
            return if (value != null) {
                Success(value)
            } else {
                Failure(false, result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }
}

fun logError(t: Throwable) {
    Log.e("Error", t.message ?: "Unknown error", t)
}

inline fun <T> safe(block: () -> T): T? {
    return try {
        block()
    } catch (e: Throwable) {
        logError(e)
        null
    }
}

suspend inline fun <T> safeAsync(crossinline block: suspend () -> T): T? {
    return try {
        block()
    } catch (e: Throwable) {
        logError(e)
        null
    }
}

suspend inline fun <T> normalSafeApiCall(crossinline block: suspend () -> T): Resource<T> {
    return try {
        Resource.Success(block())
    } catch (e: Throwable) {
        logError(e)
        Resource.Failure(false, e.message ?: "API error")
    }
}
