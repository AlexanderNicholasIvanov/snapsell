package com.alexivanov.snapsell.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SuggestedPriceTest {
    @Test
    fun `matches the backend example`() {
        // contracts/examples/price.response.json: median 89.99, factor 0.85 -> 75
        assertEquals(75.0, SuggestedPrice.recompute(89.99, 0.85)!!, 0.0)
    }

    @Test
    fun `factor change moves through price points`() {
        assertEquals(90.0, SuggestedPrice.recompute(89.99, 1.0)!!, 0.0)
        assertEquals(45.0, SuggestedPrice.recompute(89.99, 0.5)!!, 0.0)
        assertEquals(110.0, SuggestedPrice.recompute(89.99, 1.2)!!, 0.0)
    }

    @Test
    fun `no median means no suggestion`() {
        assertNull(SuggestedPrice.recompute(null, 0.85))
    }

    @Test
    fun `non-positive factor is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SuggestedPrice.recompute(10.0, 0.0) }
    }
}
