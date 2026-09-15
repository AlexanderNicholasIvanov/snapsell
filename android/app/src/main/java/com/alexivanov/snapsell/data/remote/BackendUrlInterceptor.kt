package com.alexivanov.snapsell.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Lets the user change the backend URL at runtime without rebuilding Retrofit.
 * Retrofit keeps resolving endpoints against the compiled-in base; this
 * interceptor swaps scheme / host / port / path prefix for the current
 * override, keeping the endpoint's own path and query.
 */
class BackendUrlInterceptor(
    compiledBaseUrl: String,
    /** The user's override, read fresh on every request so it can never be stale. Null = compiled-in base. */
    private val overrideProvider: () -> String?,
) : Interceptor {
    private val base: HttpUrl = ApiFactory.normalizeBaseUrl(compiledBaseUrl).toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Bad compiled backend URL: $compiledBaseUrl")

    /** The override in force right now, or null when it is unset, unparseable, or equal to the base. */
    fun resolveOverride(): HttpUrl? =
        overrideProvider()?.takeIf { it.isNotBlank() }?.let { ApiFactory.normalizeBaseUrl(it).toHttpUrlOrNull() }?.takeIf { it != base }

    override fun intercept(chain: Interceptor.Chain): Response {
        val target = resolveOverride() ?: return chain.proceed(chain.request())
        val request = chain.request()
        val relative = request.url.encodedPath.removePrefix(base.encodedPath.trimEnd('/'))
        val rewritten = target.newBuilder()
            .encodedPath(target.encodedPath.trimEnd('/') + relative)
            .encodedQuery(request.url.encodedQuery)
            .build()
        return chain.proceed(request.newBuilder().url(rewritten).build())
    }

    companion object {
        /** True for an absolute http/https URL ending in "/", which is what Retrofit and the interceptor need. */
        fun isValidBaseUrl(text: String): Boolean {
            val url = text.trim().toHttpUrlOrNull() ?: return false
            return (url.scheme == "http" || url.scheme == "https") && text.trim().endsWith("/")
        }
    }
}
