import CoreImage
import Foundation
import UIKit
import Vision

/// Finds the subjects in a photo with Vision's foreground instance masks and
/// renders each one on white. Mirrors ML Kit subject segmentation on Android.
final class Segmenter {
    struct Output {
        var segments: [Segment]
    }

    private let context = CIContext(options: [.useSoftwareRenderer: false])

    func segment(_ image: UIImage) async throws -> Output {
        guard let cg = image.normalized(maxLongEdge: PhotoStore.maxLongEdge).cgImage else {
            throw AppError("Couldn't read the photo.")
        }
        return try await Task.detached(priority: .userInitiated) { [self] in
            try self.run(cg)
        }.value
    }

    private func run(_ cg: CGImage) throws -> Output {
        let request = VNGenerateForegroundInstanceMaskRequest()
        let handler = VNImageRequestHandler(cgImage: cg, orientation: .up)
        try handler.perform([request])
        guard let result = request.results?.first else { return Output(segments: []) }
        let width = cg.width, height = cg.height
        var segments: [Segment] = []
        for instance in result.allInstances.sorted() {
            let mask = try result.generateScaledMaskForImage(forInstances: IndexSet(integer: instance), from: handler)
            guard let (bounds, outline) = Self.measure(mask: mask, width: width, height: height) else { continue }
            let masked = try result.generateMaskedImage(ofInstances: IndexSet(integer: instance), from: handler, croppedToInstancesExtent: false)
            let cutout = CutoutRenderer.render(masked: masked, bounds: bounds, context: context)
            segments.append(Segment(index: instance, bounds: bounds, cutout: cutout, outline: outline, manual: false))
        }
        return Output(segments: segments)
    }

    /// Bounding box + a coarse outline from a single-channel float mask.
    private static func measure(mask: CVPixelBuffer, width: Int, height: Int) -> (CGRect, [CGPoint])? {
        CVPixelBufferLockBaseAddress(mask, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(mask, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddress(mask) else { return nil }
        let mw = CVPixelBufferGetWidth(mask), mh = CVPixelBufferGetHeight(mask)
        let stride = CVPixelBufferGetBytesPerRow(mask)
        let format = CVPixelBufferGetPixelFormatType(mask)
        let sx = CGFloat(width) / CGFloat(mw), sy = CGFloat(height) / CGFloat(mh)

        func value(_ x: Int, _ y: Int) -> Float {
            let row = base.advanced(by: y * stride)
            switch format {
            case kCVPixelFormatType_OneComponent32Float: return row.assumingMemoryBound(to: Float.self)[x]
            case kCVPixelFormatType_OneComponent8: return Float(row.assumingMemoryBound(to: UInt8.self)[x]) / 255
            default: return Float(row.assumingMemoryBound(to: UInt8.self)[x]) / 255
            }
        }

        var minX = Int.max, minY = Int.max, maxX = -1, maxY = -1
        var left: [CGPoint] = [], right: [CGPoint] = []
        let step = max(1, mh / 64)
        for y in Swift.stride(from: 0, to: mh, by: 1) {
            var first = -1, last = -1
            for x in 0..<mw where value(x, y) >= 0.5 {
                if first < 0 { first = x }
                last = x
            }
            guard first >= 0 else { continue }
            minX = min(minX, first); maxX = max(maxX, last)
            minY = min(minY, y); maxY = max(maxY, y)
            if y % step == 0 {
                left.append(CGPoint(x: CGFloat(first) * sx, y: CGFloat(y) * sy))
                right.append(CGPoint(x: CGFloat(last + 1) * sx, y: CGFloat(y) * sy))
            }
        }
        guard maxX >= 0 else { return nil }
        let bounds = CGRect(
            x: CGFloat(minX) * sx, y: CGFloat(minY) * sy,
            width: CGFloat(maxX - minX + 1) * sx, height: CGFloat(maxY - minY + 1) * sy
        )
        return (bounds, left + right.reversed())
    }
}
