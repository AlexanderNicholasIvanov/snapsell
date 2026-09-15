import CoreGraphics
import Foundation
import Observation
import UIKit

/// Runs segmentation on the current photo, tracks which subjects the user
/// wants, and on confirm renders cutouts + inserts placeholder items that the
/// Confirm screen then identifies.
@MainActor
@Observable
final class ReviewViewModel {
    static let minManualSize: CGFloat = 24

    private(set) var image: UIImage?
    private(set) var segments: [Segment] = []
    private(set) var selected: Set<Int> = []
    private(set) var segmenting = false
    private(set) var committing = false
    var manualMode = false
    /// Vision could not run: boxes are the only way in.
    private(set) var outlinerUnavailable = false
    private(set) var error: String?
    /// Cutouts kept from earlier photos in this capture session.
    private(set) var pendingCount = 0

    var selectedCount: Int { selected.count + pendingCount }

    @ObservationIgnored private let app: AppContainer
    private var session: CaptureSession { app.captureSession }

    init(app: AppContainer) {
        self.app = app
        pendingCount = session.pendingCutouts.count
        guard let photo = session.current else {
            error = "No photo to review. Go back and take one."
            return
        }
        image = photo.workImage
        segments = photo.segments
        selected = photo.selected
        if photo.segments.isEmpty { segment(photo) }
    }

    private func segment(_ photo: CaptureSession.Photo) {
        segmenting = true
        error = nil
        Task {
            do {
                let found = try await app.segmenter.segment(photo.workImage).segments
                photo.segments = found
                // Everything found is selected by default; the user deselects.
                photo.selected = Set(found.map(\.index))
                segments = found
                selected = photo.selected
                manualMode = found.isEmpty
                error = found.isEmpty ? "No objects found. Draw a box around each thing you want to sell." : nil
            } catch {
                manualMode = true
                outlinerUnavailable = true
            }
            segmenting = false
        }
    }

    func toggle(_ index: Int) {
        guard let photo = session.current else { return }
        if photo.selected.contains(index) { photo.selected.remove(index) } else { photo.selected.insert(index) }
        selected = photo.selected
    }

    /// Toggle whichever segment contains the tapped point (smallest wins).
    func tap(at p: CGPoint) {
        guard let hit = segments.filter({ $0.contains(p.x, p.y) }).min(by: { $0.area < $1.area }) else { return }
        toggle(hit.index)
    }

    /// Adds a user-drawn rectangle (photo pixel coordinates) as a selected segment.
    func addManualRect(_ rect: CGRect) {
        guard let photo = session.current else { return }
        let bounds = CGRect(origin: .zero, size: photo.workImage.size)
        let clipped = rect.standardized.intersection(bounds)
        guard !clipped.isNull, clipped.width >= Self.minManualSize, clipped.height >= Self.minManualSize else { return }
        let index = (photo.segments.map(\.index).max() ?? -1) + 1
        photo.segments.append(Segment.manual(index: index, rect: clipped))
        photo.selected.insert(index)
        segments = photo.segments
        selected = photo.selected
        manualMode = false
        error = nil
    }

    func remove(_ index: Int) {
        guard let photo = session.current else { return }
        photo.segments.removeAll { $0.index == index }
        photo.selected.remove(index)
        segments = photo.segments
        selected = photo.selected
    }

    /// "Retake" for one item: drop that segment, stash every other selected
    /// cutout from this photo, then let the caller open the camera again.
    func retake(_ index: Int, onReady: @escaping () -> Void) {
        remove(index)
        Task {
            try? await stashSelected()
            onReady()
        }
    }

    private func stashSelected() async throws {
        guard let photo = session.current else { return }
        let toRender = photo.segments.filter { photo.selected.contains($0.index) }
        if toRender.isEmpty { return }
        let store = app.photoStore
        let work = photo.workImage
        let file = photo.file
        let cutouts: [CaptureSession.Cutout] = try await Task.detached(priority: .userInitiated) {
            try toRender.map { seg in
                guard let img = CutoutRenderer.render(work, seg) else { throw AppError("Couldn't render a cutout.") }
                return CaptureSession.Cutout(file: try store.saveCutout(img), originalPhoto: file)
            }
        }.value
        session.pendingCutouts.append(contentsOf: cutouts)
        photo.selected = []
        selected = []
        pendingCount = session.pendingCutouts.count
    }

    /// Render every selected cutout, insert placeholder items, hand their ids to Confirm.
    func identify(onItems: @escaping ([String]) -> Void) {
        guard !committing else { return }
        committing = true
        error = nil
        Task {
            do {
                try await stashSelected()
                let cutouts = session.pendingCutouts
                if cutouts.isEmpty {
                    committing = false
                    error = "Select at least one item."
                    return
                }
                let now = app.clock.nowMillis()
                let items = cutouts.map { cutout in
                    Item(
                        id: Ids.newId(),
                        // Blank name marks "not identified yet"; ConfirmViewModel fills it in.
                        name: "", category: "", condition: .good, searchQuery: "", confidence: 0,
                        photoFile: cutout.file, cutoutFile: cutout.file, originalPhotoFile: cutout.originalPhoto,
                        createdAt: now, updatedAt: now
                    )
                }
                try app.inventory.saveItems(items)
                session.clear()
                onItems(items.map(\.id))
            } catch {
                committing = false
                self.error = "Could not prepare cutouts: \(AppError.describe(error).message)"
            }
        }
    }
}
