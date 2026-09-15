import Foundation

/// ISO 8601 UTC timestamps as used by the contracts ("2026-09-14T17:00:00Z").
enum Iso8601 {
    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.isLenient = false
        return f
    }()

    static func format(_ epochMillis: Int64) -> String {
        formatter.string(from: Date(timeIntervalSince1970: TimeInterval(epochMillis) / 1000))
    }

    static func parse(_ text: String) throws -> Int64 {
        guard let date = formatter.date(from: text) else { throw AppError("Bad ISO 8601 timestamp: \(text)") }
        return Int64((date.timeIntervalSince1970 * 1000).rounded())
    }
}
