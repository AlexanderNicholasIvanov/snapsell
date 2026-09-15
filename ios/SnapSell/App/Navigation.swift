import SwiftUI

/// Mirrors the Android routes. Inventory is the root; everything else pushes.
enum Route: Hashable {
    case capture
    case review
    case confirm(itemIds: [String])
    case item(id: String)
    case bundle(itemId: String?)
    case handoff(listingId: String)
    case settings
}

@MainActor
@Observable
final class Router {
    var path: [Route] = []
    /// Inventory tab to show after `goHome`: 0 items, 1 listings.
    var inventoryTab = 0

    func push(_ r: Route) { path.append(r) }
    func pop() { if !path.isEmpty { path.removeLast() } }
    func goHome(tab: Int = 0) { inventoryTab = tab; path = [] }

    /// Replace the top route (Capture <-> Review swap on Android).
    func replaceTop(with r: Route) {
        if path.isEmpty { path = [r] } else { path[path.count - 1] = r }
    }

    /// After Review commits: Confirm sits directly above Inventory.
    func toConfirm(_ ids: [String]) { path = [.confirm(itemIds: ids)] }
}

struct RootView: View {
    @Environment(AppContainer.self) private var app
    @State private var router = Router()

    var body: some View {
        Group {
            if app.auth.isAuthenticated {
                NavigationStack(path: $router.path) {
                    InventoryScreen()
                        .navigationDestination(for: Route.self) { route in
                            switch route {
                            case .capture: CaptureScreen()
                            case .review: ReviewScreen()
                            case .confirm(let ids): ConfirmScreen(itemIds: ids)
                            case .item(let id): ItemDetailScreen(itemId: id)
                            case .bundle(let itemId): BundleBuilderScreen(initialItemId: itemId)
                            case .handoff(let id): HandOffScreen(listingId: id)
                            case .settings: SettingsScreen()
                            }
                        }
                }
                .toolbar(.hidden, for: .navigationBar)
                .environment(router)
            } else {
                SignInScreen()
            }
        }
        .onChange(of: app.auth.isAuthenticated) { _, on in if !on { router.goHome() } }
        .onAppear { applyDebugStartRoute() }
    }

    /// Debug builds honour `-snapsell_start_route <capture|review|bundle|settings>`
    /// so a screen can be opened straight from `simctl launch` for UI checks.
    private func applyDebugStartRoute() {
        #if DEBUG
        guard app.auth.isAuthenticated, router.path.isEmpty,
              let name = UserDefaults.standard.string(forKey: "snapsell_start_route") else { return }
        switch name {
        case "capture": router.push(.capture)
        case "review": router.push(.review)
        case "bundle": router.push(.bundle(itemId: nil))
        case "settings": router.push(.settings)
        default: break
        }
        #endif
    }
}

/// Every pushed screen hides the system bar and uses SnapTopBar instead.
extension View {
    func snapScreen() -> some View {
        self.toolbar(.hidden, for: .navigationBar).navigationBarBackButtonHidden(true).screenBackground()
    }
}

extension UIApplication {
    /// The view controller to present sheets (Google Sign-In) from.
    var topViewController: UIViewController? {
        let scene = connectedScenes.compactMap { $0 as? UIWindowScene }.first { $0.activationState == .foregroundActive }
            ?? connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.keyWindow?.rootViewController ?? scene?.windows.first?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
