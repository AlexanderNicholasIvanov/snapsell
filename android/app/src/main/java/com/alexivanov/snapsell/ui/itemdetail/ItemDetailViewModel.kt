package com.alexivanov.snapsell.ui.itemdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.core.AppResult
import com.alexivanov.snapsell.data.local.ItemEntity
import com.alexivanov.snapsell.data.local.toDto
import com.alexivanov.snapsell.domain.ListingKind
import com.alexivanov.snapsell.domain.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ItemDetailUiState(
    val loading: Boolean = true,
    val item: ItemEntity? = null,
    val priceText: String = "",
    val pricing: Boolean = false,
    val error: String? = null,
    val localSaleFactor: Double = 0.85,
)

class ItemDetailViewModel(private val container: AppContainer, private val itemId: String) : ViewModel() {
    private val local = MutableStateFlow(ItemDetailUiState())
    private var priceTextTouched = false

    val state: StateFlow<ItemDetailUiState> = combine(
        container.inventory.observeItem(itemId),
        container.settings.localSaleFactor,
        local,
    ) { item, factor, l ->
        val priceText = if (priceTextTouched) l.priceText else item?.finalPrice?.let(Money::plain).orEmpty()
        l.copy(loading = false, item = item, priceText = priceText, localSaleFactor = factor)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailUiState())

    /** Typing a final price overrides the suggestion everywhere; clearing the field removes the override. */
    fun editPrice(text: String) {
        priceTextTouched = true
        local.update { it.copy(priceText = text, error = null) }
        viewModelScope.launch {
            if (text.isBlank()) {
                container.inventory.setFinalPrice(itemId, null)
            } else {
                text.toDoubleOrNull()?.takeIf { it >= 0 }?.let { p -> container.inventory.setFinalPrice(itemId, p) }
            }
        }
    }

    fun reprice() {
        val item = state.value.item ?: return
        local.update { it.copy(pricing = true, error = null) }
        viewModelScope.launch {
            val factor = container.settings.currentLocalSaleFactor()
            when (val r = container.pricing.price(item.toDto(), factor)) {
                is AppResult.Success -> {
                    container.inventory.saveQuote(itemId, r.value)
                    local.update { it.copy(pricing = false) }
                }
                is AppResult.Failure -> local.update { it.copy(pricing = false, error = r.message) }
            }
        }
    }

    /** Creates a single-item draft listing and returns its id via [onCreated]. */
    fun createListing(onCreated: (String) -> Unit) {
        val item = state.value.item ?: return
        val price = state.value.priceText.toDoubleOrNull() ?: item.finalPrice ?: item.quote?.suggestedPrice
        if (price == null || price < 0) {
            local.update { it.copy(error = "Set a price first.") }
            return
        }
        viewModelScope.launch {
            val id = container.inventory.createListing(
                kind = ListingKind.SINGLE,
                itemIds = listOf(itemId),
                title = item.listingTitle?.takeIf { it.isNotBlank() } ?: item.name,
                description = item.listingDescription?.takeIf { it.isNotBlank() } ?: defaultDescription(item),
                price = price,
            )
            onCreated(id)
        }
    }

    private fun defaultDescription(item: ItemEntity): String = buildString {
        append(item.name)
        if (item.attributes.isNotEmpty()) append(", ").append(item.attributes.joinToString(", "))
        append(". Condition: ").append(item.condition.label.lowercase()).append('.')
        item.notes?.takeIf { it.isNotBlank() }?.let { append(' ').append(it) }
        append(" Local pickup.")
    }
}
