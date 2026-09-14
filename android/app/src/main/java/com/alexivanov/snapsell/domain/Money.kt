package com.alexivanov.snapsell.domain

import java.util.Locale

/** USD display helpers shared by the UI and the clipboard block. */
object Money {
    /** "75" for whole dollars, "79.99" otherwise. No currency symbol. */
    fun plain(amount: Double): String =
        if (amount == Math.floor(amount) && !amount.isInfinite()) {
            String.format(Locale.US, "%d", amount.toLong())
        } else {
            String.format(Locale.US, "%.2f", amount)
        }

    /** "$75" / "$79.99". */
    fun usd(amount: Double): String = "$" + plain(amount)
}
