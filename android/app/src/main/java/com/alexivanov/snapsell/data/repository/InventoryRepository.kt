package com.alexivanov.snapsell.data.repository

import com.alexivanov.snapsell.core.Clock
import com.alexivanov.snapsell.core.Ids
import com.alexivanov.snapsell.data.local.ItemEntity
import com.alexivanov.snapsell.data.local.ListingEntity
import com.alexivanov.snapsell.data.local.ListingWithItems
import com.alexivanov.snapsell.data.local.SnapsellDatabase
import com.alexivanov.snapsell.data.remote.dto.PriceQuote
import com.alexivanov.snapsell.domain.ListingKind
import com.alexivanov.snapsell.domain.ListingStatus
import com.alexivanov.snapsell.domain.SuggestedPrice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** On-device inventory (Room). Every write stamps updatedAt from [clock]. */
class InventoryRepository(
    private val db: SnapsellDatabase,
    private val clock: Clock,
) {
    private val items get() = db.itemDao()
    private val listings get() = db.listingDao()

    fun observeItems(): Flow<List<ItemEntity>> = items.observeAll()
    fun observeItem(id: String): Flow<ItemEntity?> = items.observe(id)
    suspend fun getItem(id: String): ItemEntity? = items.get(id)
    suspend fun getItems(ids: List<String>): List<ItemEntity> {
        // IN (...) does not preserve order; callers care about it.
        val byId = items.getByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    suspend fun saveItem(item: ItemEntity) = items.upsert(item.copy(updatedAt = clock.nowMillis()))
    suspend fun saveItems(list: List<ItemEntity>) = items.upsertAll(list.map { it.copy(updatedAt = clock.nowMillis()) })
    suspend fun deleteItem(id: String) = items.delete(id)

    /** Stores a fresh quote. A final price the user typed is never touched. */
    suspend fun saveQuote(itemId: String, quote: PriceQuote) {
        val existing = items.get(itemId) ?: return
        items.upsert(existing.copy(quote = quote, updatedAt = clock.nowMillis()))
    }

    /** null clears the override so the suggestion shows again. */
    suspend fun setFinalPrice(itemId: String, price: Double?) {
        val existing = items.get(itemId) ?: return
        items.upsert(existing.copy(finalPrice = price, updatedAt = clock.nowMillis()))
    }

    /**
     * Settings changed the local-sale factor: every stored quote gets a new
     * suggested_price from its own asking_median, locally. Final prices are
     * user input and are left alone.
     */
    suspend fun recomputeSuggestedPrices(localSaleFactor: Double) {
        val all = items.observeAll().first()
        val now = clock.nowMillis()
        val updated = all.mapNotNull { item ->
            val quote = item.quote ?: return@mapNotNull null
            val next = quote.copy(
                suggestedPrice = SuggestedPrice.recompute(quote.askingMedian, localSaleFactor),
                localSaleFactor = localSaleFactor,
            )
            if (next == quote) null else item.copy(quote = next, updatedAt = now)
        }
        if (updated.isNotEmpty()) items.upsertAll(updated)
    }

    fun observeListings(): Flow<List<ListingWithItems>> = listings.observeAllWithItems()
    fun observeListing(id: String): Flow<ListingWithItems?> = listings.observeWithItems(id)
    suspend fun getListing(id: String): ListingWithItems? = listings.getWithItems(id)

    /** Creates a draft listing and returns its id. */
    suspend fun createListing(
        kind: ListingKind,
        itemIds: List<String>,
        title: String,
        description: String,
        price: Double,
        bundleDiscount: Double? = null,
    ): String {
        val now = clock.nowMillis()
        val id = Ids.newId()
        listings.insertWithItems(
            ListingEntity(
                id = id,
                kind = kind,
                title = title,
                description = description,
                price = price,
                bundleDiscount = bundleDiscount,
                status = ListingStatus.DRAFT,
                createdAt = now,
                updatedAt = now,
            ),
            itemIds,
        )
        return id
    }

    suspend fun updateListingStatus(id: String, status: ListingStatus) {
        val existing = listings.getWithItems(id)?.listing ?: return
        val now = clock.nowMillis()
        listings.updateStatus(
            id = id,
            status = status,
            listedAt = if (status == ListingStatus.LISTED) existing.listedAt ?: now else existing.listedAt,
            soldAt = if (status == ListingStatus.SOLD) existing.soldAt ?: now else existing.soldAt,
            updatedAt = now,
        )
    }
}
