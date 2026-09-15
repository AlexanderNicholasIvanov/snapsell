import XCTest
@testable import SnapSell

final class PricingTests: XCTestCase {
    func testPricePointsRounding() {
        XCTAssertEqual(PricePoints.round(7.4), 7)
        XCTAssertEqual(PricePoints.round(19.6), 20)
        XCTAssertEqual(PricePoints.round(23), 25)
        XCTAssertEqual(PricePoints.round(97.4), 95)
        XCTAssertEqual(PricePoints.round(104), 100)
        XCTAssertEqual(PricePoints.round(146), 150)
    }

    func testSuggestedPriceRecompute() throws {
        XCTAssertNil(try SuggestedPrice.recompute(askingMedian: nil, localSaleFactor: 0.85))
        XCTAssertEqual(try SuggestedPrice.recompute(askingMedian: 100, localSaleFactor: 0.85), 85)
        XCTAssertEqual(try SuggestedPrice.recompute(askingMedian: 10, localSaleFactor: 0.85), 9)
        XCTAssertThrowsError(try SuggestedPrice.recompute(askingMedian: 100, localSaleFactor: 0))
    }

    func testBundlePricing() throws {
        XCTAssertEqual(try BundlePricing.compute(itemPrices: [10, 20, 30], discount: 0.8), 48)
        XCTAssertEqual(try BundlePricing.compute(itemPrices: [10, 15], discount: 1.0), 25)
        XCTAssertEqual(try BundlePricing.compute(itemPrices: [33.33], discount: 0.5), 17)
        XCTAssertEqual(try BundlePricing.compute(itemPrices: [], discount: 0.8), 0)
        XCTAssertThrowsError(try BundlePricing.compute(itemPrices: [10], discount: 0))
        XCTAssertThrowsError(try BundlePricing.compute(itemPrices: [-1], discount: 0.8))
    }
}
