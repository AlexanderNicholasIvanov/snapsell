package com.alexivanov.snapsell.contracts

import com.alexivanov.snapsell.data.remote.SnapsellJson
import com.alexivanov.snapsell.data.remote.dto.BundleRequest
import com.alexivanov.snapsell.data.remote.dto.BundleResponse
import com.alexivanov.snapsell.data.remote.dto.IdentifyResponse
import com.alexivanov.snapsell.data.remote.dto.ItemDto
import com.alexivanov.snapsell.data.remote.dto.ListingDto
import com.alexivanov.snapsell.data.remote.dto.PriceQuote
import com.alexivanov.snapsell.data.remote.dto.PriceRequest
import com.alexivanov.snapsell.data.remote.dto.SoldEstimateSource
import com.alexivanov.snapsell.domain.Condition
import com.alexivanov.snapsell.domain.ListingKind
import com.alexivanov.snapsell.domain.ListingStatus
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Decodes every fixture in /contracts/examples into its Kotlin model, then
 * checks decode -> encode -> decode is lossless and that the encoded JSON has
 * exactly the fixture's key set (so a renamed @SerialName cannot slip by).
 *
 * Gradle runs unit tests with the module directory (android/app) as the
 * working directory, hence the relative path.
 */
class ContractsRoundTripTest {
    private val examples = File("../../contracts/examples")

    private fun <T> roundTrip(fileName: String, serializer: KSerializer<T>): T {
        val file = File(examples, fileName)
        assertTrue("fixture missing: ${file.absolutePath}", file.exists())
        val text = file.readText()
        val decoded = SnapsellJson.decodeFromString(serializer, text)
        val encoded = SnapsellJson.encodeToString(serializer, decoded)
        val again = SnapsellJson.decodeFromString(serializer, encoded)
        assertEquals("round trip of $fileName", decoded, again)
        assertSameKeys(fileName, SnapsellJson.parseToJsonElement(text).jsonObject, SnapsellJson.parseToJsonElement(encoded).jsonObject)
        return decoded
    }

    /** Recursively compare key sets of nested objects (arrays compared element-wise). */
    private fun assertSameKeys(path: String, expected: JsonObject, actual: JsonObject) {
        assertEquals("keys at $path", expected.keys, actual.keys)
        for ((k, v) in expected) {
            val a = actual.getValue(k)
            if (v is JsonObject && a is JsonObject) assertSameKeys("$path.$k", v, a)
            if (v is kotlinx.serialization.json.JsonArray && a is kotlinx.serialization.json.JsonArray) {
                assertEquals("array size at $path.$k", v.size, a.size)
                v.zip(a).forEachIndexed { i, (ev, av) ->
                    if (ev is JsonObject && av is JsonObject) assertSameKeys("$path.$k[$i]", ev, av)
                }
            }
        }
    }

    @Test
    fun item() {
        val item = roundTrip("item.json", ItemDto.serializer())
        assertEquals("Apple iPad Air 2 64GB Wi-Fi", item.name)
        assertEquals(Condition.GOOD, item.condition)
        assertEquals(4, item.attributes.size)
        assertEquals(0.86, item.confidence, 0.0)
    }

    @Test
    fun identifyResponse() {
        val r = roundTrip("identify.response.json", IdentifyResponse.serializer())
        assertEquals("req_example_identify", r.requestId)
        assertEquals("Apple", r.item.brand)
        assertTrue(r.listingText.title.length <= 99)
    }

    @Test
    fun priceRequest() {
        val r = roundTrip("price.request.json", PriceRequest.serializer())
        assertEquals(0.85, r.localSaleFactor, 0.0)
        assertEquals(null, r.item.notes)
    }

    @Test
    fun priceResponse() {
        val q = roundTrip("price.response.json", PriceQuote.serializer())
        assertEquals(75.0, q.suggestedPrice!!, 0.0)
        assertEquals("USD", q.currency)
        assertEquals(9, q.compCount)
        assertTrue(q.conditionFiltered)
        assertEquals(1, q.comps.size)
        assertEquals("https://www.ebay.com/itm/123456789012", q.comps[0].url)
        assertEquals(SoldEstimateSource.LLM_ESTIMATE, q.estimatedSold!!.source)
        assertEquals(55.0, q.estimatedSold.low, 0.0)
    }

    @Test
    fun priceResponseWithNulls() {
        // Not a fixture: the schema allows nulls when no comps exist; make sure we decode them.
        val json = """{"suggested_price":null,"currency":"USD","asking_median":null,"asking_low":null,"asking_high":null,
            "comp_count":0,"condition_filtered":false,"local_sale_factor":0.85,"comps":[],"estimated_sold":null,
            "search_query":"x","request_id":"r"}"""
        val q = SnapsellJson.decodeFromString(PriceQuote.serializer(), json)
        assertEquals(null, q.suggestedPrice)
        assertEquals(null, q.estimatedSold)
        assertEquals(q, SnapsellJson.decodeFromString(PriceQuote.serializer(), SnapsellJson.encodeToString(PriceQuote.serializer(), q)))
    }

    @Test
    fun bundleRequest() {
        val r = roundTrip("bundle.request.json", BundleRequest.serializer())
        assertEquals(2, r.items.size)
        assertEquals(160.0, r.bundlePrice, 0.0)
        assertEquals(120.0, r.items[0].price, 0.0)
    }

    @Test
    fun bundleResponse() {
        val r = roundTrip("bundle.response.json", BundleResponse.serializer())
        assertEquals("req_example_bundle", r.requestId)
    }

    @Test
    fun listing() {
        val l = roundTrip("listing.json", ListingDto.serializer())
        assertEquals(ListingKind.SINGLE, l.kind)
        assertEquals(ListingStatus.DRAFT, l.status)
        assertEquals(null, l.remoteId)
        assertEquals(null, l.bundleDiscount)
        assertEquals(1, l.itemIds.size)
        assertEquals("2026-09-14T17:00:00Z", l.createdAt)
    }

    @Test
    fun unknownKeysAreIgnored() {
        val json = """{"title":"t","description":"d","request_id":"r","extra":1}"""
        val r = SnapsellJson.decodeFromString(BundleResponse.serializer(), json)
        assertEquals("t", r.title)
    }
}
