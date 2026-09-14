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
    AppResult.Failure(e.message ?: e::class.java.simpleName, e)
}
