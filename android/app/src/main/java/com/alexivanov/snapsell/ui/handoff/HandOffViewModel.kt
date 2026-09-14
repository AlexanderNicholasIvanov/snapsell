package com.alexivanov.snapsell.ui.handoff

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.data.local.ListingWithItems
import com.alexivanov.snapsell.domain.ListingStatus
import com.alexivanov.snapsell.handoff.ClipboardStager
import com.alexivanov.snapsell.handoff.MarketplaceLauncher
import com.alexivanov.snapsell.handoff.ResaleAlbum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class HandOffUiState(
    val loading: Boolean = true,
    val listing: ListingWithItems? = null,
    val staging: Boolean = false,
    /** Set once Facebook was opened; the next resume asks for the outcome. */
    val launched: Boolean = false,
    val askOutcome: Boolean = false,
    val savedCount: Int = 0,
    val route: MarketplaceLauncher.Route? = null,
    val error: String? = null,
)

class HandOffViewModel(private val container: AppContainer, private val listingId: String) : ViewModel() {
    private val local = MutableStateFlow(HandOffUiState())

    val state: StateFlow<HandOffUiState> = combine(container.inventory.observeListing(listingId), local) { l, s ->
        s.copy(loading = false, listing = l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HandOffUiState())

    /** Album + clipboard + open Facebook. [context] should be an Activity for the Custom Tab. */
    fun stageAndOpen(context: Context) {
        val listing = state.value.listing ?: return
        if (local.value.staging) return
        local.update { it.copy(staging = true, error = null) }
        viewModelScope.launch {
            try {
                val files = LinkedHashSet<File>()
                listing.items.forEach { item ->
                    files += File(item.photoPath)
                    // The original photo shows the item in context; buyers like both.
                    if (item.originalPhotoPath != item.photoPath) files += File(item.originalPhotoPath)
                }
                val existing = files.filter { it.exists() }
                val saved = withContext(container.dispatchers.io) { ResaleAlbum.save(context.applicationContext, existing) }
                ClipboardStager.copy(context, listing.listing.title, listing.listing.price, listing.listing.description)
                val route = MarketplaceLauncher.open(context)
                local.update {
                    it.copy(
                        staging = false,
                        launched = route != MarketplaceLauncher.Route.NONE,
                        savedCount = saved.size,
                        route = route,
                        error = if (route == MarketplaceLauncher.Route.NONE) "No app could open Facebook Marketplace." else null,
                    )
                }
            } catch (e: Exception) {
                local.update { it.copy(staging = false, error = "Hand-off failed: ${e.message}") }
            }
        }
    }

    fun copyTextAgain(context: Context) {
        val l = state.value.listing ?: return
        ClipboardStager.copy(context, l.listing.title, l.listing.price, l.listing.description)
    }

    fun onResumed() {
        if (local.value.launched) local.update { it.copy(askOutcome = true) }
    }

    fun dismissOutcome() = local.update { it.copy(askOutcome = false, launched = false) }

    fun markListed(onDone: () -> Unit) = setStatus(ListingStatus.LISTED, onDone)
    fun markSkipped(onDone: () -> Unit) = setStatus(ListingStatus.SKIPPED, onDone)
    fun markSold(onDone: () -> Unit) = setStatus(ListingStatus.SOLD, onDone)

    private fun setStatus(status: ListingStatus, onDone: () -> Unit) {
        viewModelScope.launch {
            container.inventory.updateListingStatus(listingId, status)
            local.update { it.copy(askOutcome = false, launched = false) }
            onDone()
        }
    }
}
