import Foundation

/// Rounds a computed price to a "sensible" price point. The backend applies the
/// same rule to asking_median * local_sale_factor, so the app and the server
/// agree when the user tweaks the sale factor locally:
///   value < 20   -> nearest $1
///   value < 100  -> nearest $5
///   otherwise    -> nearest $10
enum PricePoints {
    static func round(_ value: Double) -> Double {
        let v = max(value, 0)
        let step: Double = v < 20 ? 1 : (v < 100 ? 5 : 10)
        return (v / step).rounded() * step
    }
}

/// The backend's suggested price is asking_median × local_sale_factor rounded
/// to a price point. When the user changes the factor in Settings every stored
/// quote is recomputed with this same rule, locally, with no network call.
enum SuggestedPrice {
    static func recompute(askingMedian: Double?, localSaleFactor: Double) throws -> Double? {
        guard localSaleFactor > 0 else { throw AppError("localSaleFactor must be > 0, was \(localSaleFactor)") }
        guard let median = askingMedian else { return nil }
        return PricePoints.round(median * localSaleFactor)
    }
}

/// Bundle price = sum of the individual prices times a discount, rounded to the
/// nearest whole dollar. This runs on the device; the backend only writes copy
/// for the number it is given (see contracts/bundle.request.schema.json).
enum BundlePricing {
    static let defaultDiscount = 0.8
    static let minDiscount = 0.5
    static let maxDiscount = 1.0

    static func compute(itemPrices: [Double], discount: Double = defaultDiscount) throws -> Double {
        guard discount > 0, discount <= 1 else { throw AppError("discount must be in (0, 1], was \(discount)") }
        guard itemPrices.allSatisfy({ $0 >= 0 }) else { throw AppError("item prices must be >= 0") }
        let sum = itemPrices.reduce(0, +)
        return (sum * discount).rounded()
    }
}
