package com.alexivanov.snapsell.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.BuildConfig
import com.alexivanov.snapsell.data.repository.SettingsRepository
import com.alexivanov.snapsell.ui.common.SnapTopBar
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsScreen(container: AppContainer, onSignedOut: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val factor by container.settings.localSaleFactor.collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_LOCAL_SALE_FACTOR)
    val user by container.auth.currentUser.collectAsStateWithLifecycle()
    val devBypass by container.auth.devBypass.collectAsStateWithLifecycle()
    var factorText by remember { mutableStateOf(String.format(Locale.US, "%.2f", factor)) }
    LaunchedEffect(factor) {
        if (factorText.toDoubleOrNull() != factor) factorText = String.format(Locale.US, "%.2f", factor)
    }

    Scaffold(topBar = { SnapTopBar(title = "Settings", onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Local sale factor", style = MaterialTheme.typography.titleMedium)
            Text(
                "Multiplier applied to the eBay asking median. Local, no-shipping sales usually close below eBay asking prices; 0.85 is a sensible default.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = factor.toFloat(),
                    onValueChange = { v -> scope.launch { container.settings.setLocalSaleFactor(v.toDouble()) } },
                    valueRange = SettingsRepository.MIN_LOCAL_SALE_FACTOR.toFloat()..SettingsRepository.MAX_LOCAL_SALE_FACTOR.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = factorText,
                    onValueChange = { t ->
                        factorText = t
                        t.toDoubleOrNull()?.let { v ->
                            if (v in SettingsRepository.MIN_LOCAL_SALE_FACTOR..SettingsRepository.MAX_LOCAL_SALE_FACTOR) {
                                scope.launch { container.settings.setLocalSaleFactor(v) }
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(88.dp).padding(start = 8.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Backend", style = MaterialTheme.typography.titleMedium)
            Text(container.backendUrl, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Set snapsell.backendUrl in android/local.properties to change this.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Account", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    user != null -> "Signed in as ${user?.email ?: user?.displayName ?: user?.uid}"
                    devBypass -> "Dev mode: no sign-in (no Authorization header is sent)"
                    else -> "Not signed in"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (container.auth.isConfigured) "Firebase: configured" else "Firebase: not configured (no google-services.json)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { scope.launch { container.auth.signOut(); onSignedOut() } },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Sign out") }

            Text(
                "SnapSell ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
