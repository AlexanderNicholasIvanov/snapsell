package com.alexivanov.snapsell.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.alexivanov.snapsell.data.remote.dto.PriceQuote
import com.alexivanov.snapsell.domain.Condition
import com.alexivanov.snapsell.domain.ListingKind
import com.alexivanov.snapsell.domain.ListingStatus

/**
 * One physical item. The identification fields mirror contracts/item.schema.json
 * so an ItemDto can be rebuilt for /price and /bundle. Photos are app-private
 * file paths; the album copy is made only at hand-off time.
 */
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    val remoteId: String? = null,
    val name: String,
    val brand: String? = null,
    val model: String? = null,
    val category: String,
    val condition: Condition,
    /** Stored as a JSON array string via [Converters]. */
    val attributes: List<String> = emptyList(),
    val searchQuery: String,
    val confidence: Double,
    val notes: String? = null,
    /** The image shown for this item: the cutout when one exists, else the original. */
    val photoPath: String,
    val cutoutPath: String? = null,
    val originalPhotoPath: String,
    /** Last /price result, serialized JSON via [Converters]; null until priced. */
    val quote: PriceQuote? = null,
    /** The price the user settled on; null until they accept or edit a quote. */
    val finalPrice: Double? = null,
    // Listing copy returned by /identify, kept so a single-item listing can be
    // staged without a second round trip. Not part of the contract's Item.
    val listingTitle: String? = null,
    val listingDescription: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Matches contracts/listing.schema.json; item_ids live in [ListingItemCrossRef]. */
@Entity(tableName = "listings")
data class ListingEntity(
    @PrimaryKey val id: String,
    val remoteId: String? = null,
    val kind: ListingKind,
    val title: String,
    val description: String,
    val price: Double,
    val bundleDiscount: Double? = null,
    val status: ListingStatus,
    val listedAt: Long? = null,
    val soldAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "listing_items",
    primaryKeys = ["listingId", "itemId"],
    indices = [Index("itemId")],
)
data class ListingItemCrossRef(
    val listingId: String,
    val itemId: String,
)

data class ListingWithItems(
    @Embedded val listing: ListingEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ListingItemCrossRef::class,
            parentColumn = "listingId",
            entityColumn = "itemId",
        ),
    )
    val items: List<ItemEntity>,
)
