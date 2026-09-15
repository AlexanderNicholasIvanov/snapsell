import AuthenticationServices
import SwiftUI

struct SignInScreen: View {
    @Environment(AppContainer.self) private var app
    @Environment(\.snap) private var c
    @Environment(\.colorScheme) private var scheme
    @State private var busy = false
    @State private var error: String?

    private var showBypass: Bool {
        #if DEBUG
        return true
        #else
        return !app.auth.isAvailable
        #endif
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer()
            Rectangle().fill(c.accent).frame(width: 44, height: 44)
            Text("SnapSell").snap(SnapType.wordmark).foregroundStyle(c.text).padding(.top, 20)
            Text("Photograph your stuff. Get prices from real eBay listings.")
                .font(.custom("Archivo-Regular", size: 16)).lineSpacing(5)
                .foregroundStyle(c.text.opacity(0.75))
                .frame(maxWidth: 300, alignment: .leading)
                .padding(.top, 10)
            Spacer().frame(height: 32)
            VStack(spacing: 10) {
                if app.auth.isAvailable {
                    SnapButton("Sign in with Google", systemImage: "g.circle.fill", enabled: !busy, loading: busy) { signInGoogle() }
                    SignInWithAppleButton(.signIn) { request in
                        app.auth.prepareAppleRequest(request)
                    } onCompletion: { result in
                        busy = true
                        Task {
                            if case .failure(let e) = await app.auth.completeApple(result) { error = e.message }
                            busy = false
                        }
                    }
                    .signInWithAppleButtonStyle(scheme == .dark ? .white : .black)
                    .frame(height: SnapMetrics.controlHeight)
                    .disabled(busy)
                } else {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Sign-in unavailable").font(.custom("Archivo-ExtraBold", size: 14)).foregroundStyle(c.text)
                        Text("This build has no Firebase configuration. Add GoogleService-Info.plist and rebuild to enable sign-in.")
                            .snap(SnapType.bodySmall).foregroundStyle(c.text)
                    }
                    .padding(.horizontal, 16).padding(.vertical, 14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .overlay(Rectangle().stroke(c.text, lineWidth: SnapMetrics.rule))
                }
                if showBypass {
                    #if DEBUG
                    let label = "Continue without sign-in (dev)"
                    #else
                    let label = "Continue without sign-in"
                    #endif
                    SnapButton(label, kind: .secondary) { app.settings.setDevBypassAuth(true) }
                }
                if let error { ErrorText(error) }
            }
            .padding(.bottom, 28)
        }
        .padding(.horizontal, 24)
        .screenBackground()
    }

    private func signInGoogle() {
        guard let vc = UIApplication.shared.topViewController else { error = "No window to present sign-in."; return }
        busy = true
        error = nil
        Task {
            if case .failure(let e) = await app.auth.signInWithGoogle(presenting: vc) { error = e.message }
            busy = false
        }
    }
}
