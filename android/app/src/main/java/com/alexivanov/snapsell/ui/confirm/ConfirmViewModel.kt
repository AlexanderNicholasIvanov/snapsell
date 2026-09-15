package com.alexivanov.snapsell.ui.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.core.AppResult
import com.alexivanov.snapsell.data.local.ItemEntity
import com.alexivanov.snapsell.data.local.toDto
import com.alexivanov.snapsell.data.remote.dto.PriceQuote
import com.alexivanov.snapsell.domain.Condition
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

data class ConfirmCard(
    val itemId: String,
    val photoPath: String,
    val name: String = "",
    val brand: String = "",
    val model: String = "",
    val category: String = "",
    val condition: Condition = Condition.GOOD,
    val notes: String = "",
    val attributes: List<String> = emptyList(),
    val searchQuery: String = "",
    val confidence: Double = 0.0,
    val identifying: Boolean = false,
    val identifyError: String? = null,
    val pricing: Boolean = false,
    val priceError: String? = null,
    val quote: PriceQuote? = null,
    /** True after the user confirmed the identity at least once. */
    val confirmed: Boolean = false,
) {
    val identified: Boolean get() = name.isNotBlank()
    val busy: Boolean get() = identifying || pricing
}

data class ConfirmUiState(
    val loading: Boolean = true,
    val cards: List<ConfirmCard> = emptyList(),
    val error: String? = null,
) {
    val allPriced: Boolean get() = cards.isNotEmpty() && cards.all { it.quote != null }
    val anyBusy: Boolean get() = cards.any { it.busy }
}

/**
 * Identifies each placeholder item (created by Review), lets the user edit
 * identity + condition, and prices on confirm. Editing name/model/condition
 * after a price exists re-runs /price after a short debounce, so the quote
 * never silently refers to a different item.
 */
class ConfirmViewModel(private val container: AppContainer, private val itemIds: List<String>) : ViewModel() {
    private val _state = MutableStateFlow(ConfirmUiState())
    val state: StateFlow<ConfirmUiState> = _state

    /** Backend calls are heavy (vision LLM); keep at most two in flight. */
    private val identifyLimiter = Semaphore(2)
    private val repriceJobs = HashMap<String, Job>()

    init {
        viewModelScope.launch {
            val items = container.inventory.getItems(itemIds)
            _state.update {
                it.copy(loading = false, cards = items.map(::cardFrom), error = if (items.isEmpty()) "Nothing to confirm." else null)
            }
            items.filter { it.name.isBlank() }.forEach { identify(it.id) }
        }
    }

    private fun cardFrom(item: ItemEntity) = ConfirmCard(
        itemId = item.id,
        photoPath = item.photoPath,
        name = item.name,
        brand = item.brand.orEmpty(),
        model = item.model.orEmpty(),
        category = item.category,
        condition = item.condition,
        notes = item.notes.orEmpty(),
        attributes = item.attributes,
        searchQuery = item.searchQuery,
        confidence = item.confidence,
        quote = item.quote,
        confirmed = item.quote != null,
    )

    private fun updateCard(id: String, transform: (ConfirmCard) -> ConfirmCard) {
        _state.update { s -> s.copy(cards = s.cards.map { if (it.itemId == id) transform(it) else it }) }
    }

    fun identify(id: String) {
        updateCard(id) { it.copy(identifying = true, identifyError = null) }
        viewModelScope.launch {
            val card = _state.value.cards.firstOrNull { it.itemId == id } ?: return@launch
            val result = identifyLimiter.withPermit { container.identify.identify(File(card.photoPath)) }
            when (result) {
                is AppResult.Success -> {
                    val r = result.value
                    val existing = container.inventory.getItem(id)
                    if (existing != null) {
                        container.inventory.saveItem(
                            existing.copy(
                                name = r.item.name,
                                brand = r.item.brand,
                                model = r.item.model,
                                category = r.item.category,
                                condition = r.item.condition,
                                attributes = r.item.attributes,
                                searchQuery = r.item.searchQuery,
                                confidence = r.item.confidence,
                                notes = r.item.notes,
                                listingTitle = r.listingText.title,
                                listingDescription = r.listingText.description,
                            ),
                        )
                    }
                    updateCard(id) {
                        it.copy(
                            identifying = false,
                            name = r.item.name,
                            brand = r.item.brand.orEmpty(),
                            model = r.item.model.orEmpty(),
                            category = r.item.category,
                            condition = r.item.condition,
                            notes = r.item.notes.orEmpty(),
                            attributes = r.item.attributes,
                            searchQuery = r.item.searchQuery,
                            confidence = r.item.confidence,
                        )
                    }
                }
                is AppResult.Failure -> updateCard(id) { it.copy(identifying = false, identifyError = result.message) }
            }
        }
    }

    fun editName(id: String, v: String) = edit(id, reprice = true) { it.copy(name = v) }
    fun editBrand(id: String, v: String) = edit(id, reprice = false) { it.copy(brand = v) }
    fun editModel(id: String, v: String) = edit(id, reprice = true) { it.copy(model = v) }
    fun editNotes(id: String, v: String) = edit(id, reprice = false) { it.copy(notes = v) }
    fun editCondition(id: String, v: Condition) = edit(id, reprice = true) { it.copy(condition = v) }

    private fun edit(id: String, reprice: Boolean, transform: (ConfirmCard) -> ConfirmCard) {
        updateCard(id, transform)
        val card = _state.value.cards.firstOrNull { it.itemId == id } ?: return
        if (reprice && card.quote != null) scheduleReprice(id)
    }

    private fun scheduleReprice(id: String) {
        repriceJobs[id]?.cancel()
        repriceJobs[id] = viewModelScope.launch {
            delay(REPRICE_DEBOUNCE_MS)
            confirm(id)
        }
    }

    /** Persist edits and fetch a price. */
    fun confirm(id: String) {
        val card = _state.value.cards.firstOrNull { it.itemId == id } ?: return
        if (!card.identified || card.pricing) return
        repriceJobs.remove(id)?.cancel()
        updateCard(id) { it.copy(pricing = true, priceError = null, confirmed = true) }
        viewModelScope.launch {
            val existing = container.inventory.getItem(id) ?: return@launch
            val updated = existing.copy(
                name = card.name.trim(),
                brand = card.brand.trim().ifBlank { null },
                model = card.model.trim().ifBlank { null },
                condition = card.condition,
                notes = card.notes.trim().ifBlank { null },
                category = card.category.ifBlank { "Uncategorized" },
                // Keep the model's query unless the user changed the name; then the name is the better query.
                searchQuery = if (card.name.trim() != existing.name) card.name.trim() else existing.searchQuery.ifBlank { card.name.trim() },
            )
            container.inventory.saveItem(updated)
            val factor = container.settings.currentLocalSaleFactor()
            when (val r = container.pricing.price(updated.toDto(), factor)) {
                is AppResult.Success -> {
                    container.inventory.saveQuote(id, r.value)
                    updateCard(id) { it.copy(pricing = false, quote = r.value, searchQuery = updated.searchQuery) }
                }
                is AppResult.Failure -> updateCard(id) { it.copy(pricing = false, priceError = r.message) }
            }
        }
    }

    fun confirmAll() {
        _state.value.cards.filter { it.identified && !it.pricing }.forEach { confirm(it.itemId) }
    }

    fun remove(id: String) {
        repriceJobs.remove(id)?.cancel()
        viewModelScope.launch {
            container.inventory.deleteItem(id)
            _state.update { s -> s.copy(cards = s.cards.filterNot { it.itemId == id }) }
        }
    }

    companion object {
        const val REPRICE_DEBOUNCE_MS = 1200L
    }
}
