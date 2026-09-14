package com.alexivanov.snapsell.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.data.local.ListingWithItems
import com.alexivanov.snapsell.domain.Money
import com.alexivanov.snapsell.ui.common.SnapTopBar
import com.alexivanov.snapsell.ui.common.StatusChip
import java.io.File

@Composable
fun InventoryScreen(
    container: AppContainer,
    onCapture: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenBundle: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: InventoryViewModel = viewModel { InventoryViewModel(container.inventory) }
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            SnapTopBar(title = "SnapSell") {
                IconButton(onClick = onOpenBundle) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Build a bundle") }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCapture) { Icon(Icons.Default.Add, contentDescription = "Capture items") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Items (${state.items.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Listings (${state.listings.size})") })
            }
            when (tab) {
                0 -> ItemsList(state.items, onOpenItem)
                else -> ListingsList(state.listings, onOpenListing)
            }
        }
    }
}

@Composable
private fun ItemsList(rows: List<ItemRow>, onOpenItem: (String) -> Unit) {
    if (rows.isEmpty()) {
        EmptyState("No items yet. Tap + to photograph something to sell.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { it.item.id }) { row ->
            val item = row.item
            Row(
                Modifier.fillMaxWidth().clickable { onOpenItem(item.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = File(item.photoPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(item.name.ifBlank { "Unidentified item" }, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    val price = item.finalPrice ?: item.quote?.suggestedPrice
                    Text(
                        when {
                            price != null -> Money.usd(price)
                            item.quote != null -> "No comps found"
                            else -> "Not priced"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (val s = row.listingStatus) {
                    null -> AssistChip(onClick = {}, enabled = false, label = { Text(if (item.quote != null) "Priced" else "New") })
                    else -> StatusChip(s)
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ListingsList(listings: List<ListingWithItems>, onOpenListing: (String) -> Unit) {
    if (listings.isEmpty()) {
        EmptyState("No listings yet. Price an item, then tap \"List on Marketplace\".")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(listings, key = { it.listing.id }) { l ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpenListing(l.listing.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val thumb = l.items.firstOrNull()?.photoPath
                if (thumb != null) {
                    AsyncImage(
                        model = File(thumb),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(l.listing.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(
                        "${Money.usd(l.listing.price)} · ${l.listing.kind.label} · ${l.items.size} item${if (l.items.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(l.listing.status)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
