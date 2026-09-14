package com.alexivanov.snapsell.domain

import kotlin.math.roundToLong

/**
 * Bundle price = sum of the individual prices times a discount, rounded to the
 * nearest whole dollar. This runs on the device; the backend only writes copy
 * for the number it is given (see contracts/bundle.request.schema.json).
 */
object BundlePricing {
    const val DEFAULT_DISCOUNT = 0.8
    const val MIN_DISCOUNT = 0.5
    const val MAX_DISCOUNT = 1.0

    fun compute(itemPrices: List<Double>, discount: Double = DEFAULT_DISCOUNT): Double {
        require(discount > 0.0 && discount <= 1.0) { "discount must be in (0, 1], was $discount" }
        require(itemPrices.all { it >= 0.0 }) { "item prices must be >= 0" }
        val sum = itemPrices.sum()
        return (sum * discount).roundToLong().toDouble()
    }
}
