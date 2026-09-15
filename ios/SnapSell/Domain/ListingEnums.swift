import Foundation

/// Matches contracts/listing.schema.json "status".
enum ListingStatus: String, Codable, CaseIterable {
    case draft, listed, skipped, sold

    var wire: String { rawValue }
    var label: String { rawValue.prefix(1).uppercased() + rawValue.dropFirst() }

    static func fromWire(_ value: String) throws -> ListingStatus {
        guard let s = ListingStatus(rawValue: value) else { throw AppError("Unknown listing status: \(value)") }
        return s
    }
}

/// Matches contracts/listing.schema.json "kind".
enum ListingKind: String, Codable, CaseIterable {
    case single, bundle

    var wire: String { rawValue }
    var label: String { rawValue.prefix(1).uppercased() + rawValue.dropFirst() }

    static func fromWire(_ value: String) throws -> ListingKind {
        guard let k = ListingKind(rawValue: value) else { throw AppError("Unknown listing kind: \(value)") }
        return k
    }
}
