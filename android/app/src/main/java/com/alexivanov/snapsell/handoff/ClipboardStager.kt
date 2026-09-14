package com.alexivanov.snapsell.handoff

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.alexivanov.snapsell.domain.Money

/**
 * Puts the listing text on the clipboard as one block so the user can paste
 * it into the Marketplace form. Facebook has no public listing API; the
 * clipboard + album + deep link trio is the hand-off.
 */
object ClipboardStager {
    const val LABEL = "SnapSell listing"

    /**
     * Exact block format, so pasting into the title field first and then
     * cutting the rest into the description is predictable:
     *
     *     <title>
     *
     *     $<price>
     *
     *     <description>
     */
    fun format(title: String, price: Double, description: String): String =
        buildString {
            append(title.trim())
            append("\n\n")
            append(Money.usd(price))
            append("\n\n")
            append(description.trim())
        }

    fun copy(context: Context, title: String, price: Double, description: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(LABEL, format(title, price, description)))
    }
}
