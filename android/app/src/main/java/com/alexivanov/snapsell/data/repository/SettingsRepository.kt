package com.alexivanov.snapsell.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alexivanov.snapsell.data.remote.dto.PriceRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** User settings in DataStore. */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val LOCAL_SALE_FACTOR = doublePreferencesKey("local_sale_factor")
        val DEV_BYPASS_AUTH = booleanPreferencesKey("dev_bypass_auth")
        val BACKEND_URL = stringPreferencesKey("backend_url")
        val DISMISSED_UPDATE = intPreferencesKey("dismissed_update_version_code")
    }

    companion object {
        const val MIN_LOCAL_SALE_FACTOR = 0.5
        const val MAX_LOCAL_SALE_FACTOR = 1.2
        const val DEFAULT_LOCAL_SALE_FACTOR = PriceRequest.DEFAULT_LOCAL_SALE_FACTOR
    }

    val localSaleFactor: Flow<Double> = context.settingsStore.data.map { prefs ->
        prefs[Keys.LOCAL_SALE_FACTOR] ?: DEFAULT_LOCAL_SALE_FACTOR
    }

    suspend fun currentLocalSaleFactor(): Double = localSaleFactor.first()

    suspend fun setLocalSaleFactor(value: Double) {
        val clamped = value.coerceIn(MIN_LOCAL_SALE_FACTOR, MAX_LOCAL_SALE_FACTOR)
        context.settingsStore.edit { it[Keys.LOCAL_SALE_FACTOR] = clamped }
    }

    /**
     * Escape hatch for builds without Firebase: use the app against a backend
     * running with auth disabled. AuthInterceptor sends no header while set.
     */
    val devBypassAuth: Flow<Boolean> = context.settingsStore.data.map { it[Keys.DEV_BYPASS_AUTH] ?: false }

    suspend fun setDevBypassAuth(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.DEV_BYPASS_AUTH] = enabled }
    }

    /** User override for the backend base URL; null means BuildConfig.BACKEND_URL. */
    val backendUrlOverride: Flow<String?> = context.settingsStore.data.map { it[Keys.BACKEND_URL]?.takeIf { v -> v.isNotBlank() } }

    suspend fun setBackendUrlOverride(url: String?) {
        context.settingsStore.edit { prefs ->
            if (url.isNullOrBlank()) prefs.remove(Keys.BACKEND_URL) else prefs[Keys.BACKEND_URL] = url
        }
    }

    /** The newest update version code the user tapped "Not now" on. */
    val dismissedUpdateVersionCode: Flow<Int> = context.settingsStore.data.map { it[Keys.DISMISSED_UPDATE] ?: 0 }

    suspend fun setDismissedUpdateVersionCode(code: Int) {
        context.settingsStore.edit { it[Keys.DISMISSED_UPDATE] = code }
    }
}
