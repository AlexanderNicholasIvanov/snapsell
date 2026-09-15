package com.alexivanov.snapsell

import android.app.Application
import com.alexivanov.snapsell.auth.AuthManager
import com.alexivanov.snapsell.core.Clock
import com.alexivanov.snapsell.core.DefaultDispatcherProvider
import com.alexivanov.snapsell.core.DispatcherProvider
import com.alexivanov.snapsell.core.SystemClock
import com.alexivanov.snapsell.data.local.SnapsellDatabase
import com.alexivanov.snapsell.data.remote.ApiFactory
import com.alexivanov.snapsell.data.remote.BackendUrlInterceptor
import com.alexivanov.snapsell.data.remote.SnapsellApi
import com.alexivanov.snapsell.data.repository.BundleRepository
import com.alexivanov.snapsell.data.repository.IdentifyRepository
import com.alexivanov.snapsell.data.repository.InventoryRepository
import com.alexivanov.snapsell.data.repository.PricingRepository
import com.alexivanov.snapsell.data.repository.RemoteBundleRepository
import com.alexivanov.snapsell.data.repository.RemoteIdentifyRepository
import com.alexivanov.snapsell.data.repository.RemotePricingRepository
import com.alexivanov.snapsell.data.repository.SettingsRepository
import com.alexivanov.snapsell.update.UpdateChecker
import com.alexivanov.snapsell.update.UpdateConfig
import com.alexivanov.snapsell.update.UpdateRepository
import com.alexivanov.snapsell.vision.CaptureSession
import com.alexivanov.snapsell.vision.CutoutRenderer
import com.alexivanov.snapsell.vision.PhotoStore
import com.alexivanov.snapsell.vision.Segmenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import android.util.Log
import okhttp3.OkHttpClient

/**
 * Manual dependency graph. Everything is built lazily on first use so app
 * start stays cheap; no DI framework because a single module does not need one.
 */
class AppContainer(private val app: Application) {
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val dispatchers: DispatcherProvider = DefaultDispatcherProvider
    val clock: Clock = SystemClock

    /** The compiled-in backend URL; the user may override it in Settings (see [backendUrl]). */
    val defaultBackendUrl: String = ApiFactory.normalizeBaseUrl(BuildConfig.BACKEND_URL)

    val settings: SettingsRepository by lazy { SettingsRepository(app) }
    val auth: AuthManager by lazy { AuthManager(app, settings, appScope) }

    /**
     * Rewrites request URLs to the Settings override; Retrofit itself is never
     * rebuilt. The override is read per request (a cached DataStore read on
     * OkHttp's thread) so a value saved a moment ago is honoured immediately.
     */
    private val backendUrlInterceptor: BackendUrlInterceptor by lazy {
        BackendUrlInterceptor(defaultBackendUrl) { runBlocking { settings.backendUrlOverride.first() } }
    }

    /** The backend URL in force right now (override or compiled-in default). */
    val backendUrl: StateFlow<String> by lazy {
        settings.backendUrlOverride.map { it?.let(ApiFactory::normalizeBaseUrl) ?: defaultBackendUrl }
            .stateIn(appScope, SharingStarted.Eagerly, defaultBackendUrl)
    }

    /** Backend client: carries the Firebase Bearer token. */
    val okHttp: OkHttpClient by lazy { ApiFactory.okHttp(auth, debugLogging = BuildConfig.DEBUG, urlInterceptor = backendUrlInterceptor) }

    /** Image client for Coil: NO auth interceptor, eBay thumbnails must never see our token. */
    val imageOkHttp: OkHttpClient by lazy { OkHttpClient.Builder().build() }

    val api: SnapsellApi by lazy { ApiFactory.api(defaultBackendUrl, okHttp) }

    /** GitHub Releases check. Plain client: the Firebase token must never go to GitHub. */
    val updates: UpdateRepository by lazy {
        UpdateRepository(
            checker = UpdateChecker(
                client = imageOkHttp,
                config = UpdateConfig(repo = BuildConfig.UPDATE_REPO, token = BuildConfig.UPDATE_TOKEN, manifestUrl = BuildConfig.UPDATE_MANIFEST_URL),
                log = { Log.d("UpdateChecker", it) },
            ),
            settings = settings,
            scope = appScope,
            currentVersionCode = BuildConfig.VERSION_CODE,
            currentVersionName = BuildConfig.VERSION_NAME,
        )
    }

    val db: SnapsellDatabase by lazy { SnapsellDatabase.build(app) }
    val inventory: InventoryRepository by lazy { InventoryRepository(db, clock) }

    val identify: IdentifyRepository by lazy { RemoteIdentifyRepository(api, dispatchers) }
    val pricing: PricingRepository by lazy { RemotePricingRepository(api) }
    val bundles: BundleRepository by lazy { RemoteBundleRepository(api) }

    val photoStore: PhotoStore by lazy { PhotoStore(app) }
    val segmenter: Segmenter by lazy { Segmenter(app) }
    val cutoutRenderer: CutoutRenderer by lazy { CutoutRenderer(photoStore) }
    val captureSession: CaptureSession = CaptureSession()
}
