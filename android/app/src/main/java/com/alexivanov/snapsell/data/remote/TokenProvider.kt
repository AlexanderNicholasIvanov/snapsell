package com.alexivanov.snapsell.data.remote

/** Supplies the Bearer token for backend calls, or null to send none. */
fun interface TokenProvider {
    suspend fun idToken(): String?
}
