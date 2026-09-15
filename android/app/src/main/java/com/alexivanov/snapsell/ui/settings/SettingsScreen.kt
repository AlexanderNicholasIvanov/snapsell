package com.alexivanov.snapsell.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.BuildConfig
import com.alexivanov.snapsell.R
import com.alexivanov.snapsell.data.repository.SettingsRepository
import com.alexivanov.snapsell.ui.common.LucideIcon
import com.alexivanov.snapsell.ui.common.MicroLabel
import com.alexivanov.snapsell.ui.common.Rule
import com.alexivanov.snapsell.ui.common.SecondaryButton
import com.alexivanov.snapsell.ui.common.SnapSlider
import com.alexivanov.snapsell.ui.common.SnapTextField
import com.alexivanov.snapsell.ui.common.SnapTopBar
import com.alexivanov.snapsell.ui.theme.SnapType
import com.alexivanov.snapsell.ui.theme.snapColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(container: AppContainer, onSignedOut: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val c = snapColors
    val storedFactor by container.settings.localSaleFactor.collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_LOCAL_SALE_FACTOR)
    val user by container.auth.currentUser.collectAsStateWithLifecycle()
    val devBypass by container.auth.devBypass.collectAsStateWithLifecycle()

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
                value = container.backendUrl,
                onValueChange = {},
                label = "Backend URL",
                readOnly = true,
                contentAlpha = 0.6f,
            )
            Text(
                "Set snapsell.backendUrl in android/local.properties to change this.",
                style = SnapType.fieldLabel,
                color = c.text.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 6.dp),
            )

            Rule(Modifier.padding(vertical = 24.dp), 2.dp)

            Text(
                when {
                    user != null -> "Signed in as ${user?.email ?: user?.displayName ?: user?.uid}"
                    devBypass -> "Dev mode: no sign-in (no Authorization header is sent)"
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
                "SnapSell ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE}) · Firebase ${if (container.auth.isConfigured) "configured" else "not configured"}",
                style = SnapType.fieldLabel,
                color = c.text.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
