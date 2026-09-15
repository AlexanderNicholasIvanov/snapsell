import XCTest
@testable import SnapSell

/// Same three cases as the Android ClipboardStagerTest; the text must match byte for byte.
final class ClipboardStagerTests: XCTestCase {
    func testBlockIsTitleBlankPriceBlankDescription() {
        XCTAssertEqual(
            ClipboardStager.format(title: "Apple iPad Air 2 64GB Wi-Fi, Space Gray", price: 75, description: "Works well, light scuffs on the back. Local pickup."),
            "Apple iPad Air 2 64GB Wi-Fi, Space Gray\n\n$75\n\nWorks well, light scuffs on the back. Local pickup."
        )
    }

    func testNonWholePricesKeepTwoDecimals() {
        XCTAssertEqual(ClipboardStager.format(title: "T", price: 79.99, description: "D"), "T\n\n$79.99\n\nD")
    }

    func testSurroundingWhitespaceTrimmedInnerNewlinesKept() {
        XCTAssertEqual(ClipboardStager.format(title: "  T \n", price: 5, description: "\nline one\nline two\n"), "T\n\n$5\n\nline one\nline two")
    }
}
