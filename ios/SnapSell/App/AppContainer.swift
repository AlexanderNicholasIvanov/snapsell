import Foundation
import Observation
import SwiftData

/// Hand-rolled dependency graph, one per process.
@MainActor
@Observable
final class AppContainer {
    let settings: SettingsRepository
    let clock: Clock
    let photoStore: PhotoStore
    let captureSession = CaptureSession()
    let segmenter = Segmenter()
    let auth: AuthManager
    let api: SnapsellApi
    let identify: IdentifyRepository
    let pricing: PricingRepository
    let bundles: BundleRepository
    let inventory: InventoryRepository
    /// Build-time default from Info.plist (SNAPSELL_BACKEND_URL in the xcconfig).
    let defaultBackendUrl: String

    /// The base URL in force: the Settings override when valid, else the default.
    var backendUrl: String { BackendUrl.resolve(compiled: defaultBackendUrl, override: settings.backendUrlOverride) }

    init(container: ModelContainer, settings: SettingsRepository = SettingsRepository(), clock: Clock = SystemClock(), photoStore: PhotoStore = PhotoStore()) {
        self.settings = settings
        self.clock = clock
        self.photoStore = photoStore
        let plistUrl = (Bundle.main.object(forInfoDictionaryKey: "SnapSellBackendURL") as? String).nonBlank
        defaultBackendUrl = BackendUrl.normalize(plistUrl ?? "http://localhost:8000/")
        let auth = AuthManager(settings: settings)
        self.auth = auth
        let compiled = defaultBackendUrl
        api = SnapsellApi(
            baseUrlProvider: { BackendUrl.resolve(compiled: compiled, override: settings.backendUrlOverride) },
            tokenProvider: auth
        )
        identify = RemoteIdentifyRepository(api: api)
        pricing = RemotePricingRepository(api: api)
        bundles = RemoteBundleRepository(api: api)
        inventory = InventoryRepository(container: container, clock: clock)
    }

    static func live() -> AppContainer {
        let container: ModelContainer
        do {
            container = try SnapsellStore.container()
        } catch {
            // Pre-1.0: an incompatible store is thrown away rather than migrated.
            let url = URL.applicationSupportDirectory.appendingPathComponent("snapsell.store")
            try? FileManager.default.removeItem(at: url)
            container = (try? SnapsellStore.container()) ?? (try! SnapsellStore.container(inMemory: true))
        }
        return AppContainer(container: container)
    }
}
