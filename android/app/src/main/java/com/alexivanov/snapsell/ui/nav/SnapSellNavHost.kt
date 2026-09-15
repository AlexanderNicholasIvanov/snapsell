package com.alexivanov.snapsell.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.ui.bundle.BundleBuilderScreen
import com.alexivanov.snapsell.ui.capture.CaptureScreen
import com.alexivanov.snapsell.ui.confirm.ConfirmScreen
import com.alexivanov.snapsell.ui.handoff.HandOffScreen
import com.alexivanov.snapsell.ui.inventory.InventoryScreen
import com.alexivanov.snapsell.ui.itemdetail.ItemDetailScreen
import com.alexivanov.snapsell.ui.review.ReviewScreen
import com.alexivanov.snapsell.ui.settings.SettingsScreen
import com.alexivanov.snapsell.ui.signin.SignInScreen

object Routes {
    const val SIGN_IN = "signin"
    const val INVENTORY = "inventory?tab={tab}"
    const val CAPTURE = "capture"
    const val REVIEW = "review"
    const val CONFIRM = "confirm/{itemIds}"
    const val ITEM = "item/{itemId}"
    const val BUNDLE = "bundle?itemId={itemId}"
    const val HANDOFF = "handoff/{listingId}"
    const val SETTINGS = "settings"

    fun inventory(tab: Int = 0) = "inventory?tab=$tab"
    fun confirm(itemIds: List<String>) = "confirm/${itemIds.joinToString(",")}"
    fun item(itemId: String) = "item/$itemId"
    fun bundle(itemId: String? = null) = if (itemId == null) "bundle" else "bundle?itemId=$itemId"
    fun handoff(listingId: String) = "handoff/$listingId"
}

@Composable
fun SnapSellNavHost(container: AppContainer) {
    val nav: NavHostController = rememberNavController()
    val start = remember { if (container.auth.isSignedIn) Routes.inventory() else Routes.SIGN_IN }

    fun goHome(tab: Int = 0) {
        nav.navigate(Routes.inventory(tab)) {
            popUpTo(nav.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.SIGN_IN) {
            SignInScreen(container = container, onSignedIn = { goHome() })
        }
        composable(
            Routes.INVENTORY,
            arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
        ) { entry ->
            InventoryScreen(
                container = container,
                initialTab = entry.arguments?.getInt("tab") ?: 0,
                onCapture = { nav.navigate(Routes.CAPTURE) },
                onOpenItem = { nav.navigate(Routes.item(it)) },
                onOpenListing = { nav.navigate(Routes.handoff(it)) },
                onOpenBundle = { nav.navigate(Routes.bundle()) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CAPTURE) {
            CaptureScreen(
                container = container,
                onReview = { nav.navigate(Routes.REVIEW) { popUpTo(Routes.CAPTURE) { inclusive = true } } },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.REVIEW) {
            ReviewScreen(
                container = container,
                onRetake = { nav.navigate(Routes.CAPTURE) { popUpTo(Routes.REVIEW) { inclusive = true } } },
                onConfirm = { ids ->
                    nav.navigate(Routes.confirm(ids)) { popUpTo(Routes.INVENTORY) }
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.CONFIRM,
            arguments = listOf(navArgument("itemIds") { type = NavType.StringType }),
        ) { entry ->
            val ids = entry.arguments?.getString("itemIds").orEmpty().split(',').filter { it.isNotBlank() }
            ConfirmScreen(
                container = container,
                itemIds = ids,
                onOpenItem = { nav.navigate(Routes.item(it)) },
                onDone = { goHome() },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.ITEM,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) { entry ->
            val itemId = entry.arguments?.getString("itemId").orEmpty()
            ItemDetailScreen(
                container = container,
                itemId = itemId,
                onHandOff = { nav.navigate(Routes.handoff(it)) },
                onAddToBundle = { nav.navigate(Routes.bundle(it)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.BUNDLE,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            BundleBuilderScreen(
                container = container,
                initialItemId = entry.arguments?.getString("itemId"),
                onHandOff = { nav.navigate(Routes.handoff(it)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.HANDOFF,
            arguments = listOf(navArgument("listingId") { type = NavType.StringType }),
        ) { entry ->
            HandOffScreen(
                container = container,
                listingId = entry.arguments?.getString("listingId").orEmpty(),
                onFinished = { tab -> goHome(tab) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onSignedOut = {
                    nav.navigate(Routes.SIGN_IN) { popUpTo(nav.graph.id) { inclusive = true } }
                },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
