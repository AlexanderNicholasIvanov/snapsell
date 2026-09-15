import Foundation

/// Item condition. Wire names match contracts/item.schema.json exactly.
enum Condition: String, Codable, CaseIterable, Identifiable {
    case new
    case likeNew = "like_new"
    case good
    case fair
    case forParts = "for_parts"

    var id: String { rawValue }
    var wire: String { rawValue }

    var label: String {
        switch self {
        case .new: return "New"
        case .likeNew: return "Like new"
        case .good: return "Good"
        case .fair: return "Fair"
        case .forParts: return "For parts"
        }
    }

    static func fromWire(_ value: String) throws -> Condition {
        guard let c = Condition(rawValue: value) else { throw AppError("Unknown condition: \(value)") }
        return c
    }
}
