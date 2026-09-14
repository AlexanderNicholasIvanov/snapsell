package com.alexivanov.snapsell.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alexivanov.snapsell.domain.ListingStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = actions,
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier, label: String? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (label == null) {
            CircularProgressIndicator()
        } else {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(label, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ErrorText(message: String?, modifier: Modifier = Modifier) {
    if (!message.isNullOrBlank()) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
    }
}

@Composable
fun StatusChip(status: ListingStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container = when (status) {
        ListingStatus.DRAFT -> scheme.surfaceVariant
        ListingStatus.LISTED -> scheme.primaryContainer
        ListingStatus.SKIPPED -> scheme.errorContainer
        ListingStatus.SOLD -> scheme.tertiaryContainer
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(status.label) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = container,
            disabledLabelColor = scheme.onSurface,
        ),
        modifier = modifier,
    )
}
