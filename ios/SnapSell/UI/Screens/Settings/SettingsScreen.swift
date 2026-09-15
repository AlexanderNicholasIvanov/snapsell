import SwiftUI

struct SettingsScreen: View {
    @Environment(AppContainer.self) private var app
    @Environment(Router.self) private var router
    @Environment(\.snap) private var c
    // The slider drags a local percent; the store (and every quote) updates when the drag ends.
    @State private var percent: Double = 85
    @State private var dragging = false
    @State private var urlText = ""
    @State private var urlTouched = false

    private var urlValid: Bool { BackendUrl.isValid(urlText) }
    private var version: String { (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? "?" }
    private var build: String { (Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String) ?? "?" }
    private var buildType: String {
        #if DEBUG
        return "debug"
        #else
        return "release"
        #endif
    }

    var body: some View {
        VStack(spacing: 0) {
            SnapTopBar("Settings", onBack: { router.pop() })
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Text("Local-sale factor").font(.custom("Archivo-ExtraBold", size: 16)).foregroundStyle(c.text)
                        Spacer()
                        Text("\(Int(percent.rounded()))%").font(.custom("Archivo-ExtraBold", size: 26)).foregroundStyle(c.accent700)
                    }
                    Text("Local buyers pay less than eBay buyers. Suggested prices are this share of the eBay asking median.")
                        .snap(SnapType.bodySmall).foregroundStyle(c.text.opacity(0.7)).padding(.top, 6)
                    Slider(value: $percent, in: (SettingsRepository.minLocalSaleFactor * 100)...(SettingsRepository.maxLocalSaleFactor * 100), step: 1) { editing in
                        dragging = editing
                        if !editing { commitFactor() }
                    }
                    .tint(c.accent).padding(.top, 16)
                    HStack { MicroLabel("50%"); Spacer(); MicroLabel("85% default"); Spacer(); MicroLabel("120%") }
                    Rule().padding(.vertical, 24)
                    SnapTextField(label: "Backend URL", text: $urlText, keyboard: .URL, error: urlValid ? nil : "Must be an absolute http:// or https:// address ending in /")
                        .textInputAutocapitalization(.never)
                        .onChange(of: urlText) { _, t in editUrl(t) }
                    Text("Where the SnapSell backend runs. Ask whoever set up the app if you are not sure.")
                        .snap(SnapType.fieldLabel).foregroundStyle(c.text.opacity(0.55)).textCase(nil).padding(.top, 6)
                    if app.backendUrl != app.defaultBackendUrl || urlText != app.defaultBackendUrl {
                        SnapButton("Reset to default", kind: .ghost, fullWidth: false) {
                            urlTouched = false
                            urlText = app.defaultBackendUrl
                            app.settings.setBackendUrlOverride(nil)
                        }
                        .padding(.top, 4)
                    }
                    Rule().padding(.vertical, 24)
                    Text("App version").font(.custom("Archivo-SemiBold", size: 16)).foregroundStyle(c.text)
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 2) {
                            MicroLabel("Installed")
                            Text("v\(version)").snap(SnapType.statValue).foregroundStyle(c.text)
                            Text("build \(build) · \(buildType)").snap(SnapType.bodySmall).foregroundStyle(c.text.opacity(0.6))
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        VStack(alignment: .leading, spacing: 2) {
                            MicroLabel("Updates")
                            Text("TestFlight").snap(SnapType.statValue).foregroundStyle(c.text)
                            Text("new builds arrive through the TestFlight app").snap(SnapType.bodySmall).foregroundStyle(c.text.opacity(0.6))
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.top, 12)
                    Rule().padding(.vertical, 24)
                    Text(signedInText).snap(SnapType.bodySmall).foregroundStyle(c.text.opacity(0.7)).padding(.bottom, 12)
                    SnapButton("Sign out", kind: .secondary, systemImage: "rectangle.portrait.and.arrow.right") {
                        app.auth.signOut()
                        app.settings.setDevBypassAuth(false)
                        router.goHome()
                    }
                    Text("Firebase \(app.auth.isAvailable ? "configured" : "not configured")")
                        .snap(SnapType.fieldLabel).foregroundStyle(c.text.opacity(0.55)).textCase(nil).padding(.top, 24)
                    Spacer(minLength: 24)
                }
                .padding(.horizontal, 16).padding(.vertical, 24)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .onAppear {
            if !dragging { percent = app.settings.localSaleFactor * 100 }
            if !urlTouched { urlText = app.backendUrl }
        }
        .snapScreen()
    }

    private var signedInText: String {
        if let u = app.auth.user { return "Signed in as \(u.email ?? u.displayName ?? u.uid)" }
        if app.settings.devBypassAuth { return "No sign-in (no Authorization header is sent)" }
        return "Not signed in"
    }

    private func commitFactor() {
        let factor = Double(Int(percent.rounded())) / 100
        app.settings.setLocalSaleFactor(factor)
        // Re-price every stored suggestion locally; user-typed final prices are untouched.
        try? app.inventory.recomputeSuggestedPrices(localSaleFactor: factor)
    }

    /// Persisted only when it is a valid absolute http(s) base ending in "/".
    private func editUrl(_ text: String) {
        urlTouched = true
        guard BackendUrl.isValid(text) else { return }
        let normalized = text.trimmingCharacters(in: .whitespacesAndNewlines)
        app.settings.setBackendUrlOverride(normalized == app.defaultBackendUrl ? nil : normalized)
    }
}
