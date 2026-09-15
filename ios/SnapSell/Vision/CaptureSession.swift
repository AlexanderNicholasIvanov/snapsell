import Foundation
import UIKit

/// In-memory state carried Capture -> Review -> Confirm. Holds images, so it
/// lives in the container rather than in navigation values. Cleared when the
/// Confirm step starts.
@MainActor
final class CaptureSession {
    /// One captured photo, downscaled for segmentation and display.
    final class Photo {
        let file: String
        let workImage: UIImage
        var segments: [Segment] = []
        var selected: Set<Int> = []
        init(file: String, workImage: UIImage) { self.file = file; self.workImage = workImage }
    }

    /// A cutout already rendered from an earlier photo in this session.
    struct Cutout { let file: String; let originalPhoto: String }

    var current: Photo?
    var pendingCutouts: [Cutout] = []

    func clear() {
        current = nil
        pendingCutouts = []
    }
}
