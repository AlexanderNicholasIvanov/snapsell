package com.alexivanov.snapsell.data.remote

import com.alexivanov.snapsell.data.remote.dto.BundleRequest
import com.alexivanov.snapsell.data.remote.dto.BundleResponse
import com.alexivanov.snapsell.data.remote.dto.IdentifyRequest
import com.alexivanov.snapsell.data.remote.dto.IdentifyResponse
import com.alexivanov.snapsell.data.remote.dto.PriceQuote
import com.alexivanov.snapsell.data.remote.dto.PriceRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** The FastAPI backend. All POSTs carry a Firebase Bearer token (AuthInterceptor). */
interface SnapsellApi {
    @GET("health")
    suspend fun health(): Response<Unit>

    @POST("identify")
    suspend fun identify(@Body body: IdentifyRequest): IdentifyResponse

    @POST("price")
    suspend fun price(@Body body: PriceRequest): PriceQuote

    @POST("bundle")
    suspend fun bundle(@Body body: BundleRequest): BundleResponse
}
