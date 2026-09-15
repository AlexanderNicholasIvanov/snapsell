import Foundation
import Observation

/// User settings in UserDefaults, observable so screens update live.
@Observable
final class SettingsRepository {
    static let minLocalSaleFactor = 0.5
    static let maxLocalSaleFactor = 1.2
    static let defaultLocalSaleFactor = PriceRequest.defaultLocalSaleFactor

    private enum Keys {
        static let localSaleFactor = "local_sale_factor"
        static let devBypassAuth = "dev_bypass_auth"
        static let backendUrl = "backend_url"
    }

    @ObservationIgnored private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let stored = defaults.object(forKey: Keys.localSaleFactor) as? Double
        localSaleFactor = stored ?? Self.defaultLocalSaleFactor
        devBypassAuth = defaults.bool(forKey: Keys.devBypassAuth)
        backendUrlOverride = defaults.string(forKey: Keys.backendUrl).nonBlank
    }

    private(set) var localSaleFactor: Double

    func setLocalSaleFactor(_ value: Double) {
        let clamped = min(max(value, Self.minLocalSaleFactor), Self.maxLocalSaleFactor)
        localSaleFactor = clamped
        defaults.set(clamped, forKey: Keys.localSaleFactor)
    }

    /// Escape hatch for builds without Firebase: use the app against a backend
    /// running with auth disabled. No Authorization header is sent while set.
    private(set) var devBypassAuth: Bool

    func setDevBypassAuth(_ enabled: Bool) {
        devBypassAuth = enabled
        defaults.set(enabled, forKey: Keys.devBypassAuth)
    }

    /// User override for the backend base URL; nil means the compiled-in default.
    private(set) var backendUrlOverride: String?

    func setBackendUrlOverride(_ url: String?) {
        let value = url.nonBlank
        backendUrlOverride = value
        if let value { defaults.set(value, forKey: Keys.backendUrl) } else { defaults.removeObject(forKey: Keys.backendUrl) }
    }
}
