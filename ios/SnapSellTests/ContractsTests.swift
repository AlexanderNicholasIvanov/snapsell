import XCTest
@testable import SnapSell

/// Round-trips every example in /contracts/examples through the DTOs and
/// checks the key set survives, so the iOS wire format stays identical to
/// the schema (and to Android).
final class ContractsTests: XCTestCase {
    private var examples: URL {
        let dir = Bundle(for: Self.self).object(forInfoDictionaryKey: "SnapSellContractsDir") as? String ?? ""
        return URL(fileURLWithPath: dir).appendingPathComponent("examples")
    }

    private func load(_ name: String) throws -> Data {
        try Data(contentsOf: examples.appendingPathComponent(name))
    }

    private func keys(_ data: Data) throws -> Set<String> {
        let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        return Set(obj?.keys ?? [:].keys)
    }

    private func roundTrip<T: Codable & Equatable>(_ type: T.Type, _ file: String) throws {
        let path = examples.appendingPathComponent(file).path
        guard FileManager.default.fileExists(atPath: path) else { return }
        let original = try load(file)
        let decoded = try SnapsellJSON.decode(type, from: original)
        let reencoded = try SnapsellJSON.encode(decoded)
        XCTAssertEqual(try keys(reencoded), try keys(original), file)
        XCTAssertEqual(try SnapsellJSON.decode(type, from: reencoded), decoded, file)
    }

    func testExamplesRoundTrip() throws {
        try XCTSkipUnless(FileManager.default.fileExists(atPath: examples.path), "contracts/examples not found at \(examples.path)")
        try roundTrip(ItemDto.self, "item.json")
        try roundTrip(IdentifyRequest.self, "identify.request.json")
        try roundTrip(IdentifyResponse.self, "identify.response.json")
        try roundTrip(PriceRequest.self, "price.request.json")
        try roundTrip(PriceQuote.self, "price.response.json")
        try roundTrip(BundleRequest.self, "bundle.request.json")
        try roundTrip(BundleResponse.self, "bundle.response.json")
        try roundTrip(ListingDto.self, "listing.json")
    }

    func testIso8601() throws {
        let millis: Int64 = 1_700_000_000_000
        XCTAssertEqual(Iso8601.format(millis), "2023-11-14T22:13:20Z")
        XCTAssertEqual(try Iso8601.parse("2023-11-14T22:13:20Z"), millis)
    }
}
