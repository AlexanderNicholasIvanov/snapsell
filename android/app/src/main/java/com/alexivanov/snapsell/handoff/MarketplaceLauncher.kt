package com.alexivanov.snapsell.handoff

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Opens Facebook Marketplace.
 *
 * Order of attempts:
 *  1. `fb://marketplace` deep link into the Facebook app.
 *     UNVERIFIED: Facebook does not document its fb:// scheme and changes it
 *     without notice. This must be tested on a real device with the current
 *     Facebook build; if it opens the app's home instead of Marketplace, or
 *     nothing at all, fall through to the web URL.
 *  2. Chrome Custom Tab on the web "create listing" page.
 *  3. Plain ACTION_VIEW on the same URL (no browser with Custom Tabs support).
 *
 * Returns which route was used, mostly for the UI copy.
 */
object MarketplaceLauncher {
    const val DEEP_LINK = "fb://marketplace"
    const val WEB_URL = "https://www.facebook.com/marketplace/create/item"

    enum class Route { APP, CUSTOM_TAB, BROWSER, NONE }

    fun open(context: Context): Route {
        val deepLink = Intent(Intent.ACTION_VIEW, DEEP_LINK.toUri())
        if (deepLink.resolveActivity(context.packageManager) != null) {
            try {
                context.startActivity(deepLink)
                return Route.APP
            } catch (_: ActivityNotFoundException) {
                // fall through
            } catch (_: SecurityException) {
                // fall through
            }
        }
        val web = WEB_URL.toUri()
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, web)
            return Route.CUSTOM_TAB
        } catch (_: ActivityNotFoundException) {
            // fall through
        }
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, web))
            Route.BROWSER
        } catch (_: ActivityNotFoundException) {
            Route.NONE
        }
    }
}
