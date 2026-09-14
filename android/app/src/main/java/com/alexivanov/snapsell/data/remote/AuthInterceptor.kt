package com.alexivanov.snapsell.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <firebase id token>` to every request.
 *
 * OkHttp interceptors are synchronous, so the suspending token fetch is
 * bridged with runBlocking on OkHttp's own dispatcher thread; that is the
 * standard pattern and never blocks the main thread. When the provider
 * returns null (Firebase not configured, or the debug "continue without
 * sign-in" flag is set) no header is sent so a backend running with auth
 * disabled accepts the call.
 */
class AuthInterceptor(private val tokenProvider: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runCatching { runBlocking { tokenProvider.idToken() } }.getOrNull()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
