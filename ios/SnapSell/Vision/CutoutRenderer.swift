import CoreImage
import UIKit

/// Turns a masked subject (transparent background) into a listing photo on a
/// white square-ish canvas with 8% padding, like the Android renderer.
enum CutoutRenderer {
    static let padding: CGFloat = 0.08

    static func render(masked: CVPixelBuffer, bounds: CGRect, context: CIContext) -> UIImage? {
        let ci = CIImage(cvPixelBuffer: masked)
        // CIImage is bottom-left origin; bounds are top-left origin.
        let h = ci.extent.height
        let flipped = CGRect(x: bounds.minX, y: h - bounds.maxY, width: bounds.width, height: bounds.height)
        let cropped = ci.cropped(to: flipped.intersection(ci.extent))
        guard !cropped.extent.isEmpty, let cg = context.createCGImage(cropped, from: cropped.extent) else { return nil }
        return onWhite(UIImage(cgImage: cg))
    }

    /// Crop a plain photo to a box and pad it on white (manual boxes).
    static func crop(_ image: UIImage, to bounds: CGRect) -> UIImage? {
        guard let cg = image.normalized(maxLongEdge: PhotoStore.maxLongEdge).cgImage,
              let cut = cg.cropping(to: bounds.integral) else { return nil }
        return onWhite(UIImage(cgImage: cut))
    }

    static func onWhite(_ subject: UIImage) -> UIImage {
        let w = subject.size.width, h = subject.size.height
        let pad = max(w, h) * padding
        let canvas = CGSize(width: (w + pad * 2).rounded(), height: (h + pad * 2).rounded())
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: canvas, format: format).image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(origin: .zero, size: canvas))
            subject.draw(in: CGRect(x: pad, y: pad, width: w, height: h))
        }
    }
}

extension CutoutRenderer {
    /// The listing image for a segment: the segmenter's cutout, else a crop of the photo.
    static func render(_ photo: UIImage, _ segment: Segment) -> UIImage? {
        segment.cutout ?? crop(photo, to: segment.bounds)
    }
}
