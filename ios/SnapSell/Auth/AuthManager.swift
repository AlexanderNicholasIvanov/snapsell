import AuthenticationServices
import CryptoKit
import FirebaseAuth
import FirebaseCore
import Foundation
import GoogleSignIn
import Observation
import UIKit

/// Firebase Auth wrapper. Firebase is only configured when a
/// GoogleService-Info.plist is bundled; without it, the app still runs against
/// a backend with auth disabled via the dev bypass toggle in Settings.
@MainActor
@Observable
final class AuthManager: TokenProvider {
    struct User: Equatable {
        var uid: String
        var email: String?
        var displayName: String?
    }

    private(set) var user: User?
    private(set) var isAvailable: Bool = false
    private var listener: AuthStateDidChangeListenerHandle?
    private var currentNonce: String?
    private let settings: SettingsRepository

    init(settings: SettingsRepository) {
        self.settings = settings
        isAvailable = FirebaseApp.app() != nil
        guard isAvailable else { return }
        user = Auth.auth().currentUser.map(Self.map)
        listener = Auth.auth().addStateDidChangeListener { [weak self] _, firebaseUser in
            Task { @MainActor in self?.user = firebaseUser.map(Self.map) }
        }
    }

    /// Signed in, or bypassing auth for development.
    var isAuthenticated: Bool { user != nil || settings.devBypassAuth }

    static func configureFirebaseIfBundled() {
        guard FirebaseApp.app() == nil,
              let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path)
        else { return }
        FirebaseApp.configure(options: options)
        if let clientId = options.clientID {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientId)
        }
    }

    static func handle(url: URL) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }

    func idToken() async -> String? {
        guard isAvailable, !settings.devBypassAuth, let current = Auth.auth().currentUser else { return nil }
        return try? await current.getIDToken()
    }

    // ---------------------------------------------------------------- Google

    func signInWithGoogle(presenting: UIViewController) async -> Result<Void, AppError> {
        guard isAvailable else { return .failure(AppError("Sign-in isn't configured in this build.")) }
        do {
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenting)
            guard let idToken = result.user.idToken?.tokenString else {
                return .failure(AppError("Google didn't return an ID token."))
            }
            let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: result.user.accessToken.tokenString)
            let auth = try await Auth.auth().signIn(with: credential)
            user = Self.map(auth.user)
            return .success(())
        } catch {
            let ns = error as NSError
            if ns.domain == kGIDSignInErrorDomain, ns.code == GIDSignInError.canceled.rawValue {
                return .failure(AppError("Sign-in cancelled."))
            }
            return .failure(AppError(error.localizedDescription))
        }
    }

    // ---------------------------------------------------------------- Apple

    /// Call from `SignInWithAppleButton.onRequest`.
    func prepareAppleRequest(_ request: ASAuthorizationAppleIDRequest) {
        let nonce = Self.randomNonce()
        currentNonce = nonce
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(nonce)
    }

    /// Call from `SignInWithAppleButton.onCompletion`.
    func completeApple(_ result: Result<ASAuthorization, Error>) async -> Result<Void, AppError> {
        guard isAvailable else { return .failure(AppError("Sign-in isn't configured in this build.")) }
        switch result {
        case .failure(let error):
            if (error as? ASAuthorizationError)?.code == .canceled { return .failure(AppError("Sign-in cancelled.")) }
            return .failure(AppError(error.localizedDescription))
        case .success(let authorization):
            guard let appleCredential = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let nonce = currentNonce,
                  let tokenData = appleCredential.identityToken,
                  let token = String(data: tokenData, encoding: .utf8)
            else { return .failure(AppError("Apple didn't return an identity token.")) }
            let credential = OAuthProvider.appleCredential(withIDToken: token, rawNonce: nonce, fullName: appleCredential.fullName)
            do {
                let auth = try await Auth.auth().signIn(with: credential)
                user = Self.map(auth.user)
                return .success(())
            } catch {
                return .failure(AppError(error.localizedDescription))
            }
        }
    }

    func signOut() {
        guard isAvailable else { return }
        try? Auth.auth().signOut()
        GIDSignIn.sharedInstance.signOut()
        user = nil
    }

    // ---------------------------------------------------------------- helpers

    private static func map(_ u: FirebaseAuth.User) -> User {
        User(uid: u.uid, email: u.email, displayName: u.displayName)
    }

    private static func randomNonce(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._")
        var bytes = [UInt8](repeating: 0, count: length)
        _ = SecRandomCopyBytes(kSecRandomDefault, length, &bytes)
        return String(bytes.map { charset[Int($0) % charset.count] })
    }

    private static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}
