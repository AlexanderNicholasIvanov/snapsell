package com.alexivanov.snapsell.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BundlePricingTest {
    @Test
    fun `sum times default discount`() {
        // 120 + 80 = 200 * 0.8 = 160, the contracts/examples/bundle.request.json number.
        assertEquals(160.0, BundlePricing.compute(listOf(120.0, 80.0)), 0.0)
    }

    @Test
    fun `explicit discount applies`() {
        assertEquals(100.0, BundlePricing.compute(listOf(50.0, 50.0), discount = 1.0), 0.0)
        assertEquals(50.0, BundlePricing.compute(listOf(50.0, 50.0), discount = 0.5), 0.0)
    }

    @Test
    fun `rounds to nearest whole dollar`() {
        // 33 * 0.8 = 26.4 -> 26 ; 37 * 0.8 = 29.6 -> 30
        assertEquals(26.0, BundlePricing.compute(listOf(33.0)), 0.0)
        assertEquals(30.0, BundlePricing.compute(listOf(37.0)), 0.0)
        // exact half rounds up: 5 * 0.9 = 4.5 -> 5
        assertEquals(5.0, BundlePricing.compute(listOf(5.0), discount = 0.9), 0.0)
    }

    @Test
    fun `empty list is zero`() {
        assertEquals(0.0, BundlePricing.compute(emptyList()), 0.0)
    }

    @Test
    fun `invalid discount throws`() {
        assertThrows(IllegalArgumentException::class.java) { BundlePricing.compute(listOf(10.0), discount = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { BundlePricing.compute(listOf(10.0), discount = -0.1) }
        assertThrows(IllegalArgumentException::class.java) { BundlePricing.compute(listOf(10.0), discount = 1.01) }
    }

    @Test
    fun `negative price throws`() {
        assertThrows(IllegalArgumentException::class.java) { BundlePricing.compute(listOf(10.0, -1.0)) }
    }
}
