import Foundation

enum Ids {
    /// Lowercase UUID, the same shape Room stores on Android.
    static func newId() -> String { UUID().uuidString.lowercased() }
}
