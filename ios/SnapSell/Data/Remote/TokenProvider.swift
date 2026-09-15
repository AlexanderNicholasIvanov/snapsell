import Foundation

/// Supplies the Bearer token for backend calls, or nil to send none.
protocol TokenProvider: AnyObject {
    func idToken() async -> String?
}
