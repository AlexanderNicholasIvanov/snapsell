package com.alexivanov.snapsell.ui.itemdetail

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.data.remote.dto.Comp
import com.alexivanov.snapsell.data.remote.dto.SoldEstimateSource
import com.alexivanov.snapsell.domain.Money
import com.alexivanov.snapsell.ui.common.ErrorText
import com.alexivanov.snapsell.ui.common.LoadingBox
import com.alexivanov.snapsell.ui.common.SnapTopBar
import java.io.File

@Composable
fun ItemDetailScreen(
    container: AppContainer,
    itemId: String,
    onHandOff: (String) -> Unit,
    onAddToBundle: (String) -> Unit,
    onBack: () -> Unit,
) {
    val vm: ItemDetailViewModel = viewModel(key = itemId) { ItemDetailViewModel(container, itemId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(topBar = { SnapTopBar(title = state.item?.name?.ifBlank { "Item" } ?: "Item", onBack = onBack) }) { padding ->
        val item = state.item
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        if (item == null) {
            Column(Modifier.padding(padding).padding(24.dp)) { Text("This item no longer exists.") }
            return@Scaffold
        }
        val quote = item.quote

        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AsyncImage(
                    model = File(item.photoPath),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)),
                )
                Text(listOfNotNull(item.brand, item.model).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${item.category} · ${item.condition.label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        if (quote == null) {
                            Text("Not priced yet.", style = MaterialTheme.typography.titleMedium)
                        } else {
                            val suggested = quote.suggestedPrice
                            Text("Suggested price", style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (suggested != null) Money.usd(suggested) else "No comps",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Asking median ${fmt(quote.askingMedian)} · low ${fmt(quote.askingLow)} · high ${fmt(quote.askingHigh)} " +
                                    "(${quote.compCount} comps × ${quote.localSaleFactor} local factor)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!quote.conditionFiltered) {
                                Text(
                                    "No exact-condition comps, used all conditions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                        OutlinedTextField(
                            value = state.priceText,
                            onValueChange = vm::editPrice,
                            label = { Text("Your price (USD)") },
                            prefix = { Text("$") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                        ErrorText(state.error, Modifier.padding(top = 8.dp))
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = vm::reprice, enabled = !state.pricing) {
                                Text(if (quote == null) "Get a price" else "Re-price (factor ${state.localSaleFactor})")
                            }
                            if (state.pricing) CircularProgressIndicator(Modifier.size(18.dp))
                        }
                    }
                }
            }

            quote?.estimatedSold?.let { est ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Estimated sold range", style = MaterialTheme.typography.labelLarge)
                            Text("${Money.usd(est.low)} – ${Money.usd(est.high)}", style = MaterialTheme.typography.titleLarge)
                            if (est.source == SoldEstimateSource.LLM_ESTIMATE) {
                                Text(
                                    "Estimate, not sales data",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                Text("Source: ${est.source.name.lowercase().replace('_', ' ')}", style = MaterialTheme.typography.labelMedium)
                            }
                            est.rationale?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                        }
                    }
                }
            }

            if (quote != null && quote.comps.isNotEmpty()) {
                item { Text("Comparable listings (${quote.searchQuery})", style = MaterialTheme.typography.titleMedium) }
                items(quote.comps, key = { it.itemId }) { comp ->
                    CompRow(comp) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, comp.url.toUri())) }
                    }
                    HorizontalDivider()
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onAddToBundle(item.id) }, modifier = Modifier.weight(1f)) { Text("Add to bundle") }
                    Button(onClick = { vm.createListing(onHandOff) }, modifier = Modifier.weight(1f)) { Text("List on Marketplace") }
                }
            }
        }
    }
}

private fun fmt(v: Double?): String = v?.let(Money::usd) ?: "–"

@Composable
private fun CompRow(comp: Comp, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = comp.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(comp.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            Text(
                "${Money.usd(comp.total)} total (${Money.usd(comp.price)} + ${Money.usd(comp.shipping)} ship)" +
                    (comp.condition?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

