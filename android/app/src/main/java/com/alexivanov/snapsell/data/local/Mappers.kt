package com.alexivanov.snapsell.data.local

import com.alexivanov.snapsell.core.Iso8601
import com.alexivanov.snapsell.data.remote.dto.ItemDto
import com.alexivanov.snapsell.data.remote.dto.ListingDto

/** The contract Item for this entity, as sent to /price and /bundle. */
fun ItemEntity.toDto(): ItemDto = ItemDto(
    name = name,
    brand = brand?.takeIf { it.isNotBlank() },
    model = model?.takeIf { it.isNotBlank() },
    category = category,
    condition = condition,
    attributes = attributes,
    searchQuery = searchQuery.ifBlank { name },
    confidence = confidence,
    notes = notes?.takeIf { it.isNotBlank() },
)

/** The contract Listing; the future sync API consumes exactly this. */
fun ListingWithItems.toDto(): ListingDto = ListingDto(
    id = listing.id,
    remoteId = listing.remoteId,
    kind = listing.kind,
    itemIds = items.map { it.id },
    title = listing.title,
    description = listing.description,
    price = listing.price,
    bundleDiscount = listing.bundleDiscount,
    status = listing.status,
    listedAt = listing.listedAt?.let(Iso8601::format),
    soldAt = listing.soldAt?.let(Iso8601::format),
    createdAt = Iso8601.format(listing.createdAt),
    updatedAt = Iso8601.format(listing.updatedAt),
)
