package com.alexivanov.snapsell.domain

/**
 * The backend's suggested price is asking_median × local_sale_factor rounded
 * to a price point. When the user changes the factor in Settings every stored
 * quote is recomputed with this same rule, locally, with no network call.
 */
object SuggestedPrice {
    fun recompute(askingMedian: Double?, localSaleFactor: Double): Double? {
        require(localSaleFactor > 0.0) { "localSaleFactor must be > 0, was $localSaleFactor" }
        if (askingMedian == null) return null
        return PricePoints.round(askingMedian * localSaleFactor)
    }
}
