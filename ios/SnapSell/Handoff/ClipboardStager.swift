import UIKit

/// Stages listing text on the clipboard so it can be pasted into Marketplace.
/// The format (title, blank line, price, blank line, description) is shared
/// with Android; see the tests.
enum ClipboardStager {
    static func format(title: String, price: Double, description: String) -> String {
        let t = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let d = description.trimmingCharacters(in: .whitespacesAndNewlines)
        return "\(t)\n\n\(Money.usd(price))\n\n\(d)"
    }

    static func stage(title: String, price: Double, description: String) {
        UIPasteboard.general.string = format(title: title, price: price, description: description)
    }
}
