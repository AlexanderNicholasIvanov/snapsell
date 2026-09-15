package com.alexivanov.snapsell.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.BuildConfig
import com.alexivanov.snapsell.R
import com.alexivanov.snapsell.data.remote.BackendUrlInterceptor
import com.alexivanov.snapsell.data.repository.SettingsRepository
import com.alexivanov.snapsell.ui.common.ErrorText
import com.alexivanov.snapsell.ui.common.GhostButton
import com.alexivanov.snapsell.ui.common.LucideIcon
import com.alexivanov.snapsell.ui.common.MicroLabel
import com.alexivanov.snapsell.ui.common.Rule
import com.alexivanov.snapsell.ui.common.PrimaryButton
import com.alexivanov.snapsell.ui.common.SecondaryButton
import com.alexivanov.snapsell.ui.common.SnapSlider
import com.alexivanov.snapsell.ui.common.SnapTextField
import com.alexivanov.snapsell.ui.common.SnapTopBar
import com.alexivanov.snapsell.ui.common.Spinner
import com.alexivanov.snapsell.ui.theme.SnapType
import com.alexivanov.snapsell.ui.theme.snapColors
import com.alexivanov.snapsell.update.ManualCheck
import com.alexivanov.snapsell.update.UpdateChecker
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(container: AppContainer, onSignedOut: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val c = snapColors
    val context = LocalContext.current
    val storedFactor by container.settings.localSaleFactor.collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_LOCAL_SALE_FACTOR)
    val user by container.auth.currentUser.collectAsStateWithLifecycle()
    val devBypass by container.auth.devBypass.collectAsStateWithLifecycle()
    val backendUrl by container.backendUrl.collectAsStateWithLifecycle()
    val manualCheck by container.updates.manual.collectAsStateWithLifecycle()

    // The slider drags a local percent; the store (and every quote) updates when the drag ends.
    var percent by remember { mutableFloatStateOf((storedFactor * 100).toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(storedFactor) { if (!dragging) percent = (storedFactor * 100).toFloat() }
    fun commit() {
        dragging = false
        val factor = percent.roundToInt() / 100.0
        scope.launch {
            container.settings.setLocalSaleFactor(factor)
            // Re-price every stored suggestion locally; user-typed final prices are untouched.
            container.inventory.recomputeSuggestedPrices(factor)
        }
    }

    // Backend URL: edited locally, persisted only when it is a valid absolute http(s) base ending in "/".
    var urlText by remember { mutableStateOf(backendUrl) }
    var urlTouched by remember { mutableStateOf(false) }
    LaunchedEffect(backendUrl) { if (!urlTouched) urlText = backendUrl }
    val urlValid = BackendUrlInterceptor.isValidBaseUrl(urlText)
    fun editUrl(text: String) {
        urlTouched = true
        urlText = text
        if (BackendUrlInterceptor.isValidBaseUrl(text)) {
            val normalized = text.trim()
            scope.launch { container.settings.setBackendUrlOverride(if (normalized == container.defaultBackendUrl) null else normalized) }
        }
    }

    Scaffold(containerColor = c.bg, topBar = { SnapTopBar(title = "Settings", onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Local-sale factor", style = SnapType.body.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold), color = c.text, modifier = Modifier.weight(1f))
                Text("${percent.roundToInt()}%", style = SnapType.cardPrice.copy(fontSize = 26.sp), color = c.accent700)
            }
            Text(
                "Local buyers pay less than eBay buyers. Suggested prices are this share of the eBay asking median.",
                style = SnapType.bodySmall,
                color = c.text.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp),
            )
            SnapSlider(
                value = percent,
                onValueChange = { dragging = true; percent = it },
                valueRange = (SettingsRepository.MIN_LOCAL_SALE_FACTOR * 100).toFloat()..(SettingsRepository.MAX_LOCAL_SALE_FACTOR * 100).toFloat(),
                onValueChangeFinished = { commit() },
                modifier = Modifier.padding(top = 16.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                MicroLabel("50%", alpha = 0.55f)
                Spacer(Modifier.weight(1f))
                MicroLabel("85% default", alpha = 0.55f)
                Spacer(Modifier.weight(1f))
                MicroLabel("120%", alpha = 0.55f)
            }

            Rule(Modifier.padding(vertical = 24.dp), 2.dp)

            SnapTextField(
                value = urlText,
                onValueChange = ::editUrl,
                label = "Backend URL",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            if (!urlValid) {
                ErrorText("Must be an absolute http:// or https:// address ending in /", Modifier.padding(top = 6.dp))
            }
            Text(
                "Where the SnapSell backend runs. Ask whoever set up the app if you are not sure.",
                style = SnapType.fieldLabel,
                color = c.text.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 6.dp),
            )
            if (backendUrl != container.defaultBackendUrl || urlText != container.defaultBackendUrl) {
                GhostButton(
                    "Reset to default",
                    onClick = {
                        urlTouched = false
                        urlText = container.defaultBackendUrl
                        scope.launch { container.settings.setBackendUrlOverride(null) }
                    },
                    minHeight = 40.dp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Rule(Modifier.padding(vertical = 24.dp), 2.dp)

            // App version: what is installed and, once known, the latest release.
            // `available` is filled by the automatic check at launch; a manual check refreshes it.
            val autoLatest by container.updates.available.collectAsStateWithLifecycle()
            val latest = (manualCheck as? ManualCheck.Available)?.info ?: autoLatest
            Text("App version", style = SnapType.rowTitle.copy(fontSize = 16.sp), color = c.text)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    MicroLabel("Installed")
                    Text("v${BuildConfig.VERSION_NAME}", style = SnapType.statValue, color = c.text)
                    Text(
                        "build ${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_TYPE}",
                        style = SnapType.bodySmall,
                        color = c.text.copy(alpha = 0.6f),
                    )
                }
                Column(Modifier.weight(1f)) {
                    MicroLabel("Latest available")
                    when {
                        latest != null -> {
                            Text(latest.versionName, style = SnapType.statValue, color = c.accent700)
                            Text(
                                UpdateChecker.formatSize(latest.sizeBytes).ifEmpty { "newer than installed" },
                                style = SnapType.bodySmall,
                                color = c.text.copy(alpha = 0.6f),
                            )
                        }
                        manualCheck == ManualCheck.UpToDate -> {
                            Text("v${BuildConfig.VERSION_NAME}", style = SnapType.statValue, color = c.text)
                            Text("you have the latest", style = SnapType.bodySmall, color = c.text.copy(alpha = 0.6f))
                        }
                        manualCheck == ManualCheck.Checking -> Spinner()
                        else -> Text("\u2014", style = SnapType.statValue, color = c.text.copy(alpha = 0.4f))
                    }
                }
            }
            if (latest != null) {
                PrimaryButton(
                    "Download ${latest.versionName}",
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, latest.apkUrl.toUri())) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    trailing = { LucideIcon(R.drawable.ic_lucide_external_link, null, tint = c.bg) },
                )
                Text(
                    "Downloads in your browser. Android asks before installing.",
                    style = SnapType.bodySmall,
                    color = c.text.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp),
                )
                GhostButton(
                    "Check again",
                    onClick = { container.updates.checkNow() },
                    enabled = manualCheck != ManualCheck.Checking,
                    minHeight = 40.dp,
                )
            } else {
                SecondaryButton(
                    "Check for updates",
                    onClick = { container.updates.checkNow() },
                    enabled = manualCheck != ManualCheck.Checking,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    trailing = if (manualCheck == ManualCheck.Checking) ({ Spinner() }) else null,
                )
                if (manualCheck == ManualCheck.Failed) ErrorText("Couldn't check for updates", Modifier.padding(top = 8.dp))
            }

            Rule(Modifier.padding(vertical = 24.dp), 2.dp)

            Text(
                when {
                    user != null -> "Signed in as ${user?.email ?: user?.displayName ?: user?.uid}"
                    devBypass -> "No sign-in (no Authorization header is sent)"
                    else -> "Not signed in"
                },
                style = SnapType.bodySmall,
                color = c.text.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp),
            )
            SecondaryButton(
                "Sign out",
                onClick = { scope.launch { container.auth.signOut(); onSignedOut() } },
                modifier = Modifier.fillMaxWidth(),
                leading = { LucideIcon(R.drawable.ic_lucide_log_out, null, size = 20.dp, tint = c.text) },
            )

            Text(
                "Firebase ${if (container.auth.isConfigured) "configured" else "not configured"}",
                style = SnapType.fieldLabel,
                color = c.text.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 24.dp),
            )
            Spacer(Modifier.width(1.dp))
        }
    }
}
