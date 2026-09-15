import Foundation

/// USD display helpers shared by the UI and the clipboard block.
enum Money {
    /// "75" for whole dollars, "79.99" otherwise. No currency symbol.
    static func plain(_ amount: Double) -> String {
        if amount == amount.rounded(.down), amount.isFinite {
            return String(Int64(amount))
        }
        return String(format: "%.2f", locale: Locale(identifier: "en_US_POSIX"), amount)
    }

    /// "$75" / "$79.99".
    static func usd(_ amount: Double) -> String { "$" + plain(amount) }
}
