import Foundation

/// Wall-clock provider; swap for a fixed clock in tests. Milliseconds since
/// the epoch, matching the Android app and the contracts' timestamps.
protocol Clock {
    func nowMillis() -> Int64
}

struct SystemClock: Clock {
    func nowMillis() -> Int64 { Int64((Date().timeIntervalSince1970 * 1000).rounded()) }
}

struct FixedClock: Clock {
    var millis: Int64
    func nowMillis() -> Int64 { millis }
}
