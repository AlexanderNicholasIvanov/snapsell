package com.alexivanov.snapsell.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.BuildConfig
import com.alexivanov.snapsell.core.AppResult
import com.alexivanov.snapsell.ui.common.ErrorText
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(container: AppContainer, onSignedIn: () -> Unit) {
    val auth = container.auth
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by auth.currentUser.collectAsStateWithLifecycle()
    val devBypass by auth.devBypass.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(user, devBypass) {
        if (user != null || devBypass) onSignedIn()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SnapSell", style = MaterialTheme.typography.displaySmall)
        Text(
            "Photograph it. Price it. List it.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )

        if (!auth.isConfigured) {
            Text(
                "Firebase is not configured in this build (no google-services.json). " +
                    "Google sign-in is unavailable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = {
                busy = true
                error = null
                scope.launch {
                    when (val r = auth.signInWithGoogle(context)) {
                        is AppResult.Success -> onSignedIn()
                        is AppResult.Failure -> error = r.message
                    }
                    busy = false
                }
            },
            enabled = auth.isConfigured && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.height(20.dp)) else Text("Sign in with Google")
        }

        if (BuildConfig.DEBUG) {
            // Debug-only: lets the app run against a backend started with auth
            // disabled. AuthInterceptor sends no Authorization header while set.
            OutlinedButton(
                onClick = { scope.launch { auth.enableDevBypass() } },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text("Continue without sign-in (dev)")
            }
        }

        ErrorText(error, modifier = Modifier.padding(top = 16.dp))
    }
}
