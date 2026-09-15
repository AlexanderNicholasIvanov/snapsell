import Foundation

/// Base-URL rules shared by the API client and the Settings screen.
enum BackendUrl {
    /// Every request path is appended to the base, so it must end in "/".
    static func normalize(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.hasSuffix("/") ? trimmed : trimmed + "/"
    }

    /// True for an absolute http/https URL with a host, ending in "/".
    static func isValid(_ text: String) -> Bool {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasSuffix("/"), let url = URL(string: trimmed), let scheme = url.scheme?.lowercased(),
              scheme == "http" || scheme == "https", let host = url.host, !host.isEmpty
        else { return false }
        return true
    }

    /// The URL in force for `override` (user setting) over `compiled` (build default).
    static func resolve(compiled: String, override: String?) -> String {
        if let o = override, !o.trimmingCharacters(in: .whitespaces).isEmpty, isValid(o) { return normalize(o) }
        return normalize(compiled)
    }
}
