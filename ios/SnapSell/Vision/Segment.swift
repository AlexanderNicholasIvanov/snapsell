import CoreGraphics
import UIKit

/// One subject found in a photo (or a box the user drew). Coordinates are in
/// the work image's pixel space.
struct Segment: Identifiable {
    let index: Int
    var bounds: CGRect
    /// Rendered subject on white, when the segmenter produced one. Manual
    /// boxes render from the photo at commit time.
    var cutout: UIImage?
    /// Coarse outline, for the overlay.
    var outline: [CGPoint]
    var manual: Bool

    var id: Int { index }

    func contains(_ x: CGFloat, _ y: CGFloat) -> Bool { bounds.contains(CGPoint(x: x, y: y)) }
    var area: CGFloat { bounds.width * bounds.height }

    static func manual(index: Int, rect: CGRect) -> Segment {
        Segment(index: index, bounds: rect, cutout: nil, outline: [], manual: true)
    }
}
