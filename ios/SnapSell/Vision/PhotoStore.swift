import Foundation
import UIKit

/// Photos live under Application Support/photos as JPEGs. The store deals in
/// file names, never absolute paths, because the container path changes.
final class PhotoStore {
    static let maxLongEdge: CGFloat = 2048
    static let workLongEdge: CGFloat = 1600
    static let jpegQuality: CGFloat = 0.92

    let dir: URL

    init(dir: URL? = nil) {
        let base = dir ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("photos", isDirectory: true)
        self.dir = base
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
    }

    func url(_ file: String) -> URL { dir.appendingPathComponent(file) }

    func load(_ file: String) -> UIImage? { UIImage(contentsOfFile: url(file).path) }

    /// Persist a capture: orientation baked in, long edge capped, JPEG.
    @discardableResult
    func saveOriginal(_ image: UIImage) throws -> String {
        let normalized = image.normalized(maxLongEdge: Self.maxLongEdge)
        return try save(normalized, prefix: "photo")
    }

    /// Persist a cutout (already rendered on white).
    @discardableResult
    func saveCutout(_ image: UIImage) throws -> String {
        try save(image, prefix: "cutout")
    }

    /// Persist a smaller copy for upload (/identify wants JPEG; keep it lean).
    func saveWork(_ image: UIImage) throws -> String {
        try save(image.normalized(maxLongEdge: Self.workLongEdge), prefix: "work")
    }

    func delete(_ file: String?) {
        guard let file else { return }
        try? FileManager.default.removeItem(at: url(file))
    }

    private func save(_ image: UIImage, prefix: String) throws -> String {
        guard let data = image.jpegData(compressionQuality: Self.jpegQuality) else {
            throw AppError("Couldn't encode the photo.")
        }
        let name = "\(prefix)-\(Ids.newId()).jpg"
        try data.write(to: url(name), options: .atomic)
        return name
    }
}

extension UIImage {
    /// Up-orientation copy, scaled so the long edge is at most `maxLongEdge`.
    func normalized(maxLongEdge: CGFloat) -> UIImage {
        let longEdge = max(size.width, size.height)
        let scale = longEdge > maxLongEdge ? maxLongEdge / longEdge : 1
        let target = CGSize(width: (size.width * scale).rounded(), height: (size.height * scale).rounded())
        if imageOrientation == .up, scale == 1, self.scale == 1 { return self }
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
    }
}
