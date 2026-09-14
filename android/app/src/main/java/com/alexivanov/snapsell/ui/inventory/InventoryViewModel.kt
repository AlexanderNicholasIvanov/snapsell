package com.alexivanov.snapsell.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexivanov.snapsell.data.local.ItemEntity
import com.alexivanov.snapsell.data.local.ListingWithItems
import com.alexivanov.snapsell.data.repository.InventoryRepository
import com.alexivanov.snapsell.domain.ListingStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class InventoryUiState(
    val loading: Boolean = true,
    val items: List<ItemRow> = emptyList(),
    val listings: List<ListingWithItems> = emptyList(),
)

/** An item plus the status of the most recent listing it belongs to, if any. */
data class ItemRow(val item: ItemEntity, val listingStatus: ListingStatus?)

class InventoryViewModel(inventory: InventoryRepository) : ViewModel() {
    val state: StateFlow<InventoryUiState> = combine(
        inventory.observeItems(),
        inventory.observeListings(),
    ) { items, listings ->
        val statusByItem = HashMap<String, ListingStatus>()
        // Listings are newest-first; keep the first status seen per item.
        for (l in listings) for (it in l.items) statusByItem.putIfAbsent(it.id, l.listing.status)
        InventoryUiState(
            loading = false,
            items = items.map { ItemRow(it, statusByItem[it.id]) },
            listings = listings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())
}
