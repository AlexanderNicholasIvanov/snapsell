package com.alexivanov.snapsell.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PricePointsTest {
    @Test
    fun `rounding table mirrors the backend rule`() {
        val table = listOf(
            // value -> expected
            0.0 to 0.0,
            0.4 to 0.0,
            0.5 to 1.0,
            7.3 to 7.0,
            12.6 to 13.0,
            19.4 to 19.0,
            19.6 to 20.0,
            // 20..100: nearest 5
            20.0 to 20.0,
            21.0 to 20.0,
            22.5 to 25.0,
            27.4 to 25.0,
            27.5 to 30.0,
            76.49 to 75.0,
            76.5 to 75.0,
            77.5 to 80.0,
            98.0 to 100.0,
            // >= 100: nearest 10
            100.0 to 100.0,
            104.0 to 100.0,
            105.0 to 110.0,
            123.0 to 120.0,
            126.0 to 130.0,
            999.0 to 1000.0,
        )
        for ((input, expected) in table) {
            assertEquals("round($input)", expected, PricePoints.round(input), 0.0)
        }
    }

    @Test
    fun `negative clamps to zero`() {
        assertEquals(0.0, PricePoints.round(-15.0), 0.0)
    }

    @Test
    fun `example quote reproduces`() {
        // contracts/examples/price.response.json: 89.99 * 0.85 = 76.49 -> 75
        assertEquals(75.0, PricePoints.round(89.99 * 0.85), 0.0)
    }
}
