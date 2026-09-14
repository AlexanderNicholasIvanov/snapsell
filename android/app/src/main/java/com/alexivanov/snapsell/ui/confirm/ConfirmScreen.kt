package com.alexivanov.snapsell.ui.confirm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.domain.Condition
import com.alexivanov.snapsell.domain.Money
import com.alexivanov.snapsell.ui.common.ErrorText
import com.alexivanov.snapsell.ui.common.LoadingBox
import com.alexivanov.snapsell.ui.common.SnapTopBar
import java.io.File

@Composable
fun ConfirmScreen(
    container: AppContainer,
    itemIds: List<String>,
    onOpenItem: (String) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val vm: ConfirmViewModel = viewModel(key = itemIds.joinToString(",")) { ConfirmViewModel(container, itemIds) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { SnapTopBar(title = "Confirm ${state.cards.size} item${if (state.cards.size == 1) "" else "s"}", onBack = onBack) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.cards.size > 1) {
                    OutlinedButton(
                        onClick = vm::confirmAll,
                        enabled = !state.anyBusy && state.cards.any { it.identified },
                        modifier = Modifier.weight(1f),
                    ) { Text("Confirm all") }
                }
                Button(onClick = onDone, enabled = state.allPriced && !state.anyBusy, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { ErrorText(state.error) }
            items(state.cards, key = { it.itemId }) { card ->
                ConfirmCardView(card, vm, onOpenItem)
            }
        }
    }
}

@Composable
private fun ConfirmCardView(card: ConfirmCard, vm: ConfirmViewModel, onOpenItem: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = File(card.photoPath),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)),
                )
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    when {
                        card.identifying -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp))
                            Text("Identifying…", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                        card.identifyError != null -> {
                            ErrorText(card.identifyError)
                            TextButton(onClick = { vm.identify(card.itemId) }) { Text("Retry") }
                        }
                        else -> {
                            if (card.category.isNotBlank()) Text(card.category, style = MaterialTheme.typography.labelMedium)
                            if (card.confidence > 0) {
                                Text("Confidence ${(card.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (card.attributes.isNotEmpty()) {
                                Text(card.attributes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = card.name, onValueChange = { vm.editName(card.itemId, it) },
                label = { Text("Name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = card.brand, onValueChange = { vm.editBrand(card.itemId, it) },
                    label = { Text("Brand") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = card.model, onValueChange = { vm.editModel(card.itemId, it) },
                    label = { Text("Model") }, singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            Text("Condition", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                items(Condition.entries) { c ->
                    FilterChip(selected = card.condition == c, onClick = { vm.editCondition(card.itemId, c) }, label = { Text(c.label) })
                }
            }
            OutlinedTextField(
                value = card.notes, onValueChange = { vm.editNotes(card.itemId, it) },
                label = { Text("Notes (flaws, what's included)") }, minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            ErrorText(card.priceError, Modifier.padding(top = 8.dp))
            val quote = card.quote
            if (quote != null && !card.pricing) {
                val price = quote.suggestedPrice
                Text(
                    if (price != null) "Suggested ${Money.usd(price)} from ${quote.compCount} comps" else "No comps found (${quote.compCount})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.remove(card.itemId) }) { Text("Remove") }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    if (quote != null) {
                        OutlinedButton(onClick = { onOpenItem(card.itemId) }, modifier = Modifier.padding(end = 8.dp)) { Text("View quote") }
                    }
                    Button(onClick = { vm.confirm(card.itemId) }, enabled = card.identified && !card.busy) {
                        if (card.pricing) {
                            CircularProgressIndicator(Modifier.height(18.dp).size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(if (quote == null) "Confirm" else "Re-price")
                        }
                    }
                }
            }
        }
    }
}
