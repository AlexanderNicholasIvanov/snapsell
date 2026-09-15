package com.alexivanov.snapsell.ui.bundle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.core.AppResult
import com.alexivanov.snapsell.data.local.ItemEntity
import com.alexivanov.snapsell.data.local.toDto
import com.alexivanov.snapsell.domain.BundlePricing
import com.alexivanov.snapsell.domain.ListingKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BundleUiState(
    val loading: Boolean = true,
    /** Only items with a price can go in a bundle. */
    val candidates: List<ItemEntity> = emptyList(),
    val selected: Set<String> = emptySet(),
    val discount: Double = BundlePricing.DEFAULT_DISCOUNT,
    /** Percent, as typed: "80". */
    val discountText: String = (BundlePricing.DEFAULT_DISCOUNT * 100).toInt().toString(),
    val writing: Boolean = false,
    val title: String = "",
    val description: String = "",
    val error: String? = null,
) {
    val selectedItems: List<ItemEntity> get() = candidates.filter { it.id in selected }
    val itemPrices: List<Double> get() = selectedItems.mapNotNull { it.price }
    val sum: Double get() = itemPrices.sum()
    val bundlePrice: Double get() = if (itemPrices.isEmpty()) 0.0 else BundlePricing.compute(itemPrices, discount)
    val hasListingText: Boolean get() = title.isNotBlank() && description.isNotBlank()
}

val ItemEntity.price: Double? get() = finalPrice ?: quote?.suggestedPrice

class BundleBuilderViewModel(private val container: AppContainer, initialItemId: String?) : ViewModel() {
    private val local = MutableStateFlow(BundleUiState(selected = setOfNotNull(initialItemId)))

    val state: StateFlow<BundleUiState> = combine(container.inventory.observeItems(), local) { items, l ->
        l.copy(loading = false, candidates = items.filter { it.price != null })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun toggle(id: String) = local.update { s ->
        val next = if (id in s.selected) s.selected - id else s.selected + id
        // Any change to the set invalidates the copy the backend wrote.
        s.copy(selected = next, title = "", description = "", error = null)
    }

    /** Slider: whole percent steps, 50-100. */
    fun setDiscountPercent(percent: Int) {
        val d = (percent / 100.0).coerceIn(BundlePricing.MIN_DISCOUNT, BundlePricing.MAX_DISCOUNT)
        local.update { it.copy(discount = d, discountText = (d * 100).toInt().toString(), title = "", description = "") }
    }

    /** Numeric field: bound to the slider when the value is in range. */
    fun setDiscountText(text: String) {
        local.update { it.copy(discountText = text.filter { ch -> ch.isDigit() }.take(3)) }
        text.toIntOrNull()?.let { p ->
            if (p in 50..100) local.update { it.copy(discount = p / 100.0, title = "", description = "") }
        }
    }

    fun editTitle(v: String) = local.update { it.copy(title = v) }
    fun editDescription(v: String) = local.update { it.copy(description = v) }

    fun writeListing() {
        val s = state.value
        if (s.selectedItems.size < 2) {
            local.update { it.copy(error = "Pick at least two items.") }
            return
        }
        local.update { it.copy(writing = true, error = null) }
        viewModelScope.launch {
            val pairs = s.selectedItems.map { it.toDto() to (it.price ?: 0.0) }
            when (val r = container.bundles.writeListing(pairs, s.bundlePrice)) {
                is AppResult.Success -> local.update { it.copy(writing = false, title = r.value.title, description = r.value.description) }
                is AppResult.Failure -> local.update { it.copy(writing = false, error = r.message) }
            }
        }
    }

    fun createListing(onCreated: (String) -> Unit) {
        val s = state.value
        if (!s.hasListingText || s.selectedItems.size < 2) return
        viewModelScope.launch {
            val id = container.inventory.createListing(
                kind = ListingKind.BUNDLE,
                itemIds = s.selectedItems.map { it.id },
                title = s.title.trim(),
                description = s.description.trim(),
                price = s.bundlePrice,
                bundleDiscount = s.discount,
            )
            onCreated(id)
        }
    }
}
