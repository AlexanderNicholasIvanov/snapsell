package com.alexivanov.snapsell.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiFactory {
    /** Retrofit insists on a trailing slash; BuildConfig values may lack one. */
    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    fun okHttp(tokenProvider: TokenProvider, debugLogging: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // Vision-LLM identification and eBay lookups take a while.
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenProvider))
        if (debugLogging) {
            // BASIC, not BODY: the identify request carries a base64 image.
            builder.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        return builder.build()
    }

    fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(normalizeBaseUrl(baseUrl))
        .client(client)
        .addConverterFactory(SnapsellJson.asConverterFactory("application/json".toMediaType()))
        .build()

    fun api(baseUrl: String, client: OkHttpClient): SnapsellApi = retrofit(baseUrl, client).create(SnapsellApi::class.java)
}
