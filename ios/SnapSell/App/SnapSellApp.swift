import SwiftUI

@main
struct SnapSellApp: App {
    @State private var container: AppContainer

    init() {
        AuthManager.configureFirebaseIfBundled()
        _container = State(initialValue: AppContainer.live())
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(container)
                .snapThemed()
                .onOpenURL { url in _ = AuthManager.handle(url: url) }
        }
    }
}
