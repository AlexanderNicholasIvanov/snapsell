import XCTest
@testable import SnapSell

final class BackendUrlTests: XCTestCase {
    func testValidation() {
        XCTAssertTrue(BackendUrl.isValid("http://localhost:8000/"))
        XCTAssertTrue(BackendUrl.isValid("https://snapsell.example.com/api/"))
        XCTAssertFalse(BackendUrl.isValid("http://localhost:8000"))
        XCTAssertFalse(BackendUrl.isValid("localhost:8000/"))
        XCTAssertFalse(BackendUrl.isValid("ftp://host/"))
        XCTAssertFalse(BackendUrl.isValid(""))
    }

    func testResolvePrefersValidOverride() {
        XCTAssertEqual(BackendUrl.resolve(compiled: "http://a/", override: nil), "http://a/")
        XCTAssertEqual(BackendUrl.resolve(compiled: "http://a/", override: "http://b/"), "http://b/")
        XCTAssertEqual(BackendUrl.resolve(compiled: "http://a", override: "nope"), "http://a/")
    }
}
