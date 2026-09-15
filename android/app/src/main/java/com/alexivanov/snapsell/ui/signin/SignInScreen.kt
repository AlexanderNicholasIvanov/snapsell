package com.alexivanov.snapsell.ui.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.BuildConfig
import com.alexivanov.snapsell.core.AppResult
import com.alexivanov.snapsell.ui.common.ErrorText
import com.alexivanov.snapsell.ui.common.PrimaryButton
import com.alexivanov.snapsell.ui.common.SecondaryButton
import com.alexivanov.snapsell.ui.common.Spinner
import com.alexivanov.snapsell.ui.theme.SnapType
import com.alexivanov.snapsell.ui.theme.snapColors
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(container: AppContainer, onSignedIn: () -> Unit) {
    val auth = container.auth
    val c = snapColors
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
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        // Bottom-weighted: the upper block is pushed down, the buttons pin to the bottom.
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
            Box(Modifier.size(44.dp).background(c.accent))
            Spacer(Modifier.height(20.dp))
            Text("SnapSell", style = SnapType.wordmark, color = c.text)
            Text(
                "Photograph your stuff. Get prices from real eBay listings.",
                style = SnapType.body.copy(fontSize = 16.sp, lineHeight = 23.sp),
                color = c.text.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 10.dp).widthIn(max = 300.dp),
            )
            Spacer(Modifier.height(32.dp))
        }

        Column(Modifier.fillMaxWidth().padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (auth.isConfigured) {
                PrimaryButton(
                    label = "Sign in with Google",
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
                    enabled = !busy,
                    minHeight = 56.dp,
                    modifier = Modifier.fillMaxWidth(),
                    leading = {
                        if (busy) {
                            Spinner(color = c.bg)
                        } else {
                            Box(Modifier.size(20.dp).background(c.bg), contentAlignment = Alignment.Center) {
                                Text("G", style = SnapType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold), color = c.accent)
                            }
                        }
                    },
                )
            } else {
                Column(Modifier.fillMaxWidth().border(2.dp, c.text).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Sign-in unavailable", style = SnapType.body.copy(fontWeight = FontWeight.ExtraBold), color = c.text)
                    Text(
                        "This build has no Firebase configuration. Add google-services.json and rebuild to enable Google sign-in.",
                        style = SnapType.bodySmall,
                        color = c.text,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Always offered when Firebase is not configured (the only way in for
            // such a build); when it is configured, only debug builds keep it.
            // AuthInterceptor sends no Authorization header while the flag is set.
            if (!auth.isConfigured || BuildConfig.DEBUG) {
                SecondaryButton(
                    label = if (BuildConfig.DEBUG) "Continue without sign-in (dev)" else "Continue without sign-in",
                    onClick = { scope.launch { auth.enableDevBypass() } },
                    minHeight = 52.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ErrorText(error)
        }
    }
}
