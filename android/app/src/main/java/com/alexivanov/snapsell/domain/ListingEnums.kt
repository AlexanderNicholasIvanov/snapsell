package com.alexivanov.snapsell.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Matches contracts/listing.schema.json "status". */
@Serializable
enum class ListingStatus(val wire: String, val label: String) {
    @SerialName("draft") DRAFT("draft", "Draft"),
    @SerialName("listed") LISTED("listed", "Listed"),
    @SerialName("skipped") SKIPPED("skipped", "Skipped"),
    @SerialName("sold") SOLD("sold", "Sold");

    companion object {
        fun fromWire(value: String): ListingStatus =
            entries.firstOrNull { it.wire == value }
                ?: throw IllegalArgumentException("Unknown listing status: $value")
    }
}

/** Matches contracts/listing.schema.json "kind". */
@Serializable
enum class ListingKind(val wire: String, val label: String) {
    @SerialName("single") SINGLE("single", "Single"),
    @SerialName("bundle") BUNDLE("bundle", "Bundle");

    companion object {
        fun fromWire(value: String): ListingKind =
            entries.firstOrNull { it.wire == value }
                ?: throw IllegalArgumentException("Unknown listing kind: $value")
    }
}
