package com.alexivanov.snapsell.data.remote.dto

import com.alexivanov.snapsell.domain.Condition
import com.alexivanov.snapsell.domain.ListingKind
import com.alexivanov.snapsell.domain.ListingStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Every class here mirrors one schema in /contracts. Field order and names
// follow the schema; snake_case keys are mapped with @SerialName so the
// Kotlin side can stay idiomatic. Optional-nullable fields default to null so
// the app can build requests without spelling every key out.

/** contracts/item.schema.json */
@Serializable
data class ItemDto(
    @SerialName("name") val name: String,
    @SerialName("brand") val brand: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("category") val category: String,
    @SerialName("condition") val condition: Condition,
    @SerialName("attributes") val attributes: List<String> = emptyList(),
    @SerialName("search_query") val searchQuery: String,
    @SerialName("confidence") val confidence: Double,
    @SerialName("notes") val notes: String? = null,
)

/** contracts/identify.request.schema.json */
@Serializable
data class IdentifyRequest(
    @SerialName("image_base64") val imageBase64: String,
    @SerialName("media_type") val mediaType: String = MEDIA_TYPE_JPEG,
    @SerialName("hint") val hint: String? = null,
) {
    companion object {
        const val MEDIA_TYPE_JPEG = "image/jpeg"
        const val MEDIA_TYPE_PNG = "image/png"
        const val MEDIA_TYPE_WEBP = "image/webp"
    }
}

/** identify.response.schema.json#/$defs/ListingText */
@Serializable
data class ListingText(
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
)

/** contracts/identify.response.schema.json */
@Serializable
data class IdentifyResponse(
    @SerialName("item") val item: ItemDto,
    @SerialName("listing_text") val listingText: ListingText,
    @SerialName("request_id") val requestId: String,
)

/** contracts/price.request.schema.json */
@Serializable
data class PriceRequest(
    @SerialName("item") val item: ItemDto,
    @SerialName("local_sale_factor") val localSaleFactor: Double = DEFAULT_LOCAL_SALE_FACTOR,
) {
    companion object {
        const val DEFAULT_LOCAL_SALE_FACTOR = 0.85
    }
}

/** price.response.schema.json#/$defs/Comp */
@Serializable
data class Comp(
    @SerialName("item_id") val itemId: String,
    @SerialName("title") val title: String,
    @SerialName("price") val price: Double,
    @SerialName("shipping") val shipping: Double,
    @SerialName("total") val total: Double,
    @SerialName("condition") val condition: String? = null,
    @SerialName("url") val url: String,
    @SerialName("image_url") val imageUrl: String? = null,
)

/** price.response.schema.json#/$defs/SoldEstimate "source" */
@Serializable
enum class SoldEstimateSource {
    @SerialName("llm_estimate") LLM_ESTIMATE,
    @SerialName("ebay_marketplace_insights") EBAY_MARKETPLACE_INSIGHTS,
    @SerialName("third_party") THIRD_PARTY,
}

/** price.response.schema.json#/$defs/SoldEstimate */
@Serializable
data class SoldEstimate(
    @SerialName("low") val low: Double,
    @SerialName("high") val high: Double,
    @SerialName("currency") val currency: String = "USD",
    @SerialName("source") val source: SoldEstimateSource,
    @SerialName("rationale") val rationale: String? = null,
)

/** contracts/price.response.schema.json (title: PriceQuote) */
@Serializable
data class PriceQuote(
    @SerialName("suggested_price") val suggestedPrice: Double?,
    @SerialName("currency") val currency: String = "USD",
    @SerialName("asking_median") val askingMedian: Double?,
    @SerialName("asking_low") val askingLow: Double?,
    @SerialName("asking_high") val askingHigh: Double?,
    @SerialName("comp_count") val compCount: Int,
    @SerialName("condition_filtered") val conditionFiltered: Boolean,
    @SerialName("local_sale_factor") val localSaleFactor: Double,
    @SerialName("comps") val comps: List<Comp> = emptyList(),
    @SerialName("estimated_sold") val estimatedSold: SoldEstimate?,
    @SerialName("search_query") val searchQuery: String,
    @SerialName("request_id") val requestId: String,
)

/** bundle.request.schema.json "items[]" */
@Serializable
data class BundleRequestItem(
    @SerialName("item") val item: ItemDto,
    @SerialName("price") val price: Double,
)

/** contracts/bundle.request.schema.json */
@Serializable
data class BundleRequest(
    @SerialName("items") val items: List<BundleRequestItem>,
    @SerialName("bundle_price") val bundlePrice: Double,
)

/** contracts/bundle.response.schema.json */
@Serializable
data class BundleResponse(
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("request_id") val requestId: String,
)

/**
 * contracts/listing.schema.json. On-device today (Room stores it as
 * ListingEntity + ListingItemCrossRef); this DTO is the sync shape.
 */
@Serializable
data class ListingDto(
    @SerialName("id") val id: String,
    @SerialName("remote_id") val remoteId: String? = null,
    @SerialName("kind") val kind: ListingKind,
    @SerialName("item_ids") val itemIds: List<String>,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("price") val price: Double,
    @SerialName("bundle_discount") val bundleDiscount: Double? = null,
    @SerialName("status") val status: ListingStatus,
    @SerialName("listed_at") val listedAt: String? = null,
    @SerialName("sold_at") val soldAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)
