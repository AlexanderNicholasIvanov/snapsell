package com.alexivanov.snapsell.auth

import android.annotation.SuppressLint
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.alexivanov.snapsell.BuildConfig
import com.alexivanov.snapsell.core.AppResult
import com.alexivanov.snapsell.core.await
import com.alexivanov.snapsell.data.remote.TokenProvider
import com.alexivanov.snapsell.data.repository.SettingsRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/**
 * Firebase Auth via Google sign-in through Credential Manager, and the
 * TokenProvider the network layer uses.
 *
 * When app/google-services.json is absent, FirebaseApp never initializes and
 * [isConfigured] is false: sign-in is impossible, and only the debug
 * "continue without sign-in" path (SettingsRepository.devBypassAuth) lets
 * the user past the SignIn screen.
 */
class AuthManager(
    private val context: Context,
    private val settings: SettingsRepository,
    appScope: CoroutineScope,
) : TokenProvider {

    val isConfigured: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val auth: FirebaseAuth? = if (isConfigured) FirebaseAuth.getInstance() else null

    private val _currentUser = MutableStateFlow(auth?.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    /** Debug-only local flag; see [SettingsRepository.devBypassAuth]. */
    val devBypass: StateFlow<Boolean> = settings.devBypassAuth.stateIn(appScope, SharingStarted.Eagerly, false)

    init {
        auth?.addAuthStateListener { _currentUser.value = it.currentUser }
    }

    /**
     * Web client id for GetGoogleIdOption. google-services.json generates the
     * `default_web_client_id` string resource; without it, fall back to the
     * gradle property (BuildConfig.GOOGLE_WEB_CLIENT_ID).
     */
    @SuppressLint("DiscouragedApi") // name lookup is the only way to reference a resource that may not exist
    fun webClientId(): String {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) {
            val fromRes = context.getString(resId)
            if (fromRes.isNotBlank()) return fromRes
        }
        return BuildConfig.GOOGLE_WEB_CLIENT_ID
    }

    /** [activityContext] must be an Activity: Credential Manager shows UI. */
    suspend fun signInWithGoogle(activityContext: Context): AppResult<FirebaseUser> {
        val firebaseAuth = auth ?: return AppResult.Failure("Firebase is not configured (missing google-services.json).")
        val clientId = webClientId()
        if (clientId.isBlank()) {
            return AppResult.Failure("No Google web client id. Set snapsell.googleWebClientId in local.properties or add google-services.json.")
        }
        return try {
            val option = GetGoogleIdOption.Builder()
                // Show all Google accounts on the device, not just previously authorized ones.
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(activityContext).getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                val result = firebaseAuth.signInWithCredential(firebaseCred).await()
                val user = result.user ?: return AppResult.Failure("Sign-in returned no user.")
                _currentUser.value = user
                AppResult.Success(user)
            } else {
                AppResult.Failure("Unexpected credential type: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            AppResult.Failure("Sign-in cancelled.", e)
        } catch (e: NoCredentialException) {
            AppResult.Failure("No Google account available on this device. Add one in system settings and try again.", e)
        } catch (e: GetCredentialException) {
            AppResult.Failure("Sign-in failed: ${e.message}", e)
        } catch (e: Exception) {
            AppResult.Failure("Sign-in failed: ${e.message}", e)
        }
    }

    suspend fun signOut() {
        auth?.signOut()
        _currentUser.value = null
        settings.setDevBypassAuth(false)
    }

    suspend fun enableDevBypass() {
        settings.setDevBypassAuth(true)
    }

    /** True when the app should proceed past the sign-in screen. */
    val isSignedIn: Boolean get() = currentUser.value != null || devBypass.value

    /** The Bearer token for backend calls; null sends no header (dev bypass / unconfigured). */
    override suspend fun idToken(): String? {
        if (settings.devBypassAuth.first()) return null
        val user = auth?.currentUser ?: return null
        return runCatching { user.getIdToken(false).await().token }.getOrNull()
    }
}
