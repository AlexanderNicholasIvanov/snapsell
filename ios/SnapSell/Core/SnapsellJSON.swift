import Foundation

/// The one encoder/decoder pair used for the network, the local store and
/// tests. Keys are spelled out with CodingKeys in the DTOs (snake_case per
/// the contracts), so no key strategy is applied here.
enum SnapsellJSON {
    static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        return e
    }()

    static let decoder = JSONDecoder()

    static func encode<T: Encodable>(_ value: T) throws -> Data { try encoder.encode(value) }
    static func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T { try decoder.decode(type, from: data) }
}
