package com.alexivanov.snapsell.domain

import kotlin.math.roundToLong

/**
 * Rounds a computed price to a "sensible" price point. The backend applies the
 * same rule to asking_median * local_sale_factor, so the app and the server
 * agree when the user tweaks the sale factor locally:
 *   value < 20   -> nearest $1
 *   value < 100  -> nearest $5
 *   otherwise    -> nearest $10
 */
object PricePoints {
    fun round(value: Double): Double {
        val v = value.coerceAtLeast(0.0)
        val step = when {
            v < 20.0 -> 1.0
            v < 100.0 -> 5.0
            else -> 10.0
        }
        return (v / step).roundToLong() * step
    }
}
