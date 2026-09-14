package com.alexivanov.snapsell.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ISO 8601 UTC timestamps as used by the contracts ("2026-09-14T17:00:00Z").
 * java.time needs core-library desugaring below API 26, so this uses the
 * old formatter, which is fine for a fixed pattern.
 */
object Iso8601 {
    private const val PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    private fun formatter() = SimpleDateFormat(PATTERN, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }

    fun format(epochMillis: Long): String = formatter().format(Date(epochMillis))

    fun parse(text: String): Long = formatter().parse(text)?.time
        ?: throw IllegalArgumentException("Bad ISO 8601 timestamp: $text")
}
