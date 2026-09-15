package com.alexivanov.snapsell.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.R
import com.alexivanov.snapsell.data.local.ListingWithItems
import com.alexivanov.snapsell.domain.ListingStatus
import com.alexivanov.snapsell.domain.Money
import com.alexivanov.snapsell.ui.common.CutoutTile
import com.alexivanov.snapsell.ui.common.GhostButton
import com.alexivanov.snapsell.ui.common.IconBox
import com.alexivanov.snapsell.ui.common.LucideIcon
import com.alexivanov.snapsell.ui.common.MicroLabel
import com.alexivanov.snapsell.ui.common.Rule
import com.alexivanov.snapsell.ui.common.SnapTopBar
import com.alexivanov.snapsell.ui.common.StatusChip
import com.alexivanov.snapsell.ui.theme.SnapType
import com.alexivanov.snapsell.ui.theme.snapColors
import java.io.File

@Composable
fun InventoryScreen(
    container: AppContainer,
    initialTab: Int = 0,
    onCapture: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenBundle: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: InventoryViewModel = viewModel { InventoryViewModel(container.inventory) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = snapColors
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            SnapTopBar(title = "SnapSell", titleStyle = SnapType.appBarTitle.copy(fontSize = 19.sp)) {
                IconBox(R.drawable.ic_lucide_layers, "Build a bundle", onOpenBundle)
                IconBox(R.drawable.ic_lucide_sliders_horizontal, "Settings", onOpenSettings)
            }
            TabRow(
                tabs = listOf("Items" to state.items.size, "Listings" to state.listings.size),
                selected = tab,
                onSelect = { tab = it },
            )
            when (tab) {
                0 -> ItemsList(state.items, onOpenItem)
                else -> ListingsList(state.listings, onOpenListing, onMarkSold = vm::markSold)
            }
        }

        // 64dp square FAB, accent fill, the only shadowed element on this screen.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 22.dp)
                .shadow(elevation = 12.dp, shape = RectangleShape, ambientColor = c.shadow, spotColor = c.shadow)
                .size(64.dp)
                .background(c.accent)
                .clickable(onClick = onCapture),
            contentAlignment = Alignment.Center,
        ) {
            LucideIcon(R.drawable.ic_lucide_camera, "Capture items", size = 26.dp, tint = c.bg)
        }
    }
}

/** Two flush tabs, 3dp accent underline on the active one, 2dp rule beneath. No indicator animation. */
@Composable
private fun TabRow(tabs: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    val c = snapColors
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, (label, count) ->
                val active = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clickable { onSelect(i) },
                ) {
                    Box(Modifier.weight(1f).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                        Text(
                            buildAnnotatedString {
                                append(label)
                                withStyle(SnapType.tabLabel.copy(fontWeight = FontWeight.Normal, color = c.text.copy(alpha = 0.55f)).toSpanStyle()) {
                                    append("  $count")
                                }
                            },
                            style = SnapType.tabLabel,
                            color = c.text,
                            modifier = Modifier.alpha(if (active) 1f else 0.5f),
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (active) c.accent else androidx.compose.ui.graphics.Color.Transparent))
                }
            }
        }
        Rule(thickness = 2.dp)
    }
}

@Composable
private fun ItemsList(rows: List<ItemRow>, onOpenItem: (String) -> Unit) {
    if (rows.isEmpty()) {
        EmptyState()
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        items(rows, key = { it.item.id }) { row -> ItemRowView(row, onOpenItem) }
    }
}

/** Comfortable row: 14 / 16dp padding, 60dp cutout, 1dp bottom divider. */
@Composable
private fun ItemRowView(row: ItemRow, onOpenItem: (String) -> Unit) {
    val c = snapColors
    val item = row.item
    Column(Modifier.fillMaxWidth().clickable { onOpenItem(item.id) }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            CutoutTile(File(item.photoPath), 60.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name.ifBlank { "Unidentified item" }, style = SnapType.rowTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.condition.label, style = SnapType.fieldLabel, color = c.text.copy(alpha = 0.6f))
                row.listingStatus?.let { StatusChip(it, Modifier.padding(top = 2.dp)) }
            }
            Column(horizontalAlignment = Alignment.End) {
                val final = item.finalPrice
                val suggested = item.quote?.suggestedPrice
                val shown = final ?: suggested
                Text(if (shown != null) Money.usd(shown) else "—", style = SnapType.rowPrice, color = c.text)
                MicroLabel(
                    when {
                        final != null -> "Final"
                        suggested != null -> "Suggested"
                        item.quote != null -> "No comps"
                        else -> "Not priced"
                    },
                )
            }
        }
        Rule(thickness = 1.dp)
    }
}

@Composable
private fun ListingsList(listings: List<ListingWithItems>, onOpenListing: (String) -> Unit, onMarkSold: (String) -> Unit) {
    if (listings.isEmpty()) {
        EmptyState()
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        items(listings, key = { it.listing.id }) { l -> ListingRowView(l, onOpenListing, onMarkSold) }
    }
}

@Composable
private fun ListingRowView(l: ListingWithItems, onOpenListing: (String) -> Unit, onMarkSold: (String) -> Unit) {
    val c = snapColors
    val n = l.items.size
    val meta = buildString {
        append("$n item"); if (n != 1) append('s')
        l.items.firstOrNull()?.let { append(" · ").append(it.condition.label) }
        if (n > 1) append(" · ").append(l.listing.kind.label)
    }
    Column(Modifier.fillMaxWidth().clickable { onOpenListing(l.listing.id) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(l.listing.title, style = SnapType.rowTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(meta, style = SnapType.fieldLabel, color = c.text.copy(alpha = 0.6f), modifier = Modifier.padding(top = 3.dp))
                }
                Text(Money.usd(l.listing.price), style = SnapType.rowPrice, color = c.text, modifier = Modifier.padding(start = 12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(l.listing.status)
                if (l.listing.status == ListingStatus.LISTED) {
                    Spacer(Modifier.width(4.dp))
                    GhostButton("Mark as sold", onClick = { onMarkSold(l.listing.id) }, minHeight = 36.dp)
                }
            }
        }
        Rule(thickness = 1.dp)
    }
}

/** Flush left at 64 / 28dp, not centred. */
@Composable
private fun EmptyState() {
    val c = snapColors
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 64.dp)) {
        Box(Modifier.size(56.dp).border(2.dp, c.text), contentAlignment = Alignment.Center) {
            LucideIcon(R.drawable.ic_lucide_camera, null, size = 26.dp, tint = c.text)
        }
        Text("Nothing here yet", style = SnapType.sectionHead.copy(fontSize = 23.sp, lineHeight = 28.sp), color = c.text, modifier = Modifier.padding(top = 20.dp))
        Text(
            "Point the camera at one item, or at a whole pile. SnapSell separates them out.",
            style = SnapType.bodyLarge,
            color = c.text.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
