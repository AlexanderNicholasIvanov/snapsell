package com.alexivanov.snapsell.core

/**
 * Minimal result wrapper for repository calls. Kept deliberately small: the UI
 * only ever needs "the value" or "a message to show".
 */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : AppResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value

    fun messageOrNull(): String? = (this as? Failure)?.message

    fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppResult.Failure) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(this)
    return this
}

/** Runs [block] and folds any thrown exception into [AppResult.Failure]. */
suspend inline fun <T> appResult(crossinline block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    AppResult.Failure(describeError(e), e)
}

/**
 * Turns an exception into something a person can act on. The backend answers
 * errors with `{"detail": "..."}`, so that text is preferred over the bare status.
 */
fun describeError(e: Exception): String = when (e) {
    is retrofit2.HttpException -> {
        val detail = runCatching {
            val body = e.response()?.errorBody()?.string().orEmpty()
            Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        }.getOrNull()
        when {
            detail != null -> "Server: $detail (HTTP ${e.code()})"
            e.code() == 401 || e.code() == 403 -> "Not signed in or not on the allowlist (HTTP ${e.code()})"
            e.code() == 429 -> "Daily limit reached on the server (HTTP 429)"
            else -> "Server error (HTTP ${e.code()})"
        }
    }
    is java.net.ConnectException, is java.net.UnknownHostException ->
        "Can't reach the backend. Is it running, and is the URL in Settings right?"
    is java.net.SocketTimeoutException -> "The backend took too long to answer."
    is java.io.IOException -> "Network error: ${e.message ?: e::class.java.simpleName}"
    else -> e.message ?: e::class.java.simpleName
}
