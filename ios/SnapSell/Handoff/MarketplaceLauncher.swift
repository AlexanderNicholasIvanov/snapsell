import SafariServices
import UIKit

/// Opens Facebook Marketplace's "create listing" flow: the Facebook app when
/// installed, else the web flow.
enum MarketplaceLauncher {
    enum Route { case app, web, none }

    static let appURL = URL(string: "fb://marketplace")!
    static let webURL = URL(string: "https://www.facebook.com/marketplace/create/item")!

    @MainActor
    static func open() async -> Route {
        let app = UIApplication.shared
        if app.canOpenURL(appURL), await app.open(appURL) { return .app }
        if await app.open(webURL) { return .web }
        return .none
    }
}
