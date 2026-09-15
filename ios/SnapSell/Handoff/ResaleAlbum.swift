import Photos
import UIKit

/// Saves listing photos into a "Resale" album in the photo library so they are
/// easy to find from Marketplace's picker.
enum ResaleAlbum {
    static let albumName = "Resale"

    enum Outcome { case savedToAlbum, savedToLibrary, denied }

    static func save(_ images: [UIImage]) async -> Outcome {
        guard !images.isEmpty else { return .savedToAlbum }
        let status = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        switch status {
        case .authorized:
            do {
                let album = try await ensureAlbum()
                try await PHPhotoLibrary.shared().performChanges {
                    let requests = images.map { PHAssetChangeRequest.creationRequestForAsset(from: $0) }
                    if let albumRequest = PHAssetCollectionChangeRequest(for: album) {
                        let placeholders = requests.compactMap(\.placeholderForCreatedAsset)
                        albumRequest.addAssets(placeholders as NSArray)
                    }
                }
                return .savedToAlbum
            } catch {
                return await saveLoose(images) ? .savedToLibrary : .denied
            }
        case .limited:
            return await saveLoose(images) ? .savedToLibrary : .denied
        default:
            // Add-only permission (or none): still try the plain save.
            return await saveLoose(images) ? .savedToLibrary : .denied
        }
    }

    private static func saveLoose(_ images: [UIImage]) async -> Bool {
        do {
            try await PHPhotoLibrary.shared().performChanges {
                images.forEach { PHAssetChangeRequest.creationRequestForAsset(from: $0) }
            }
            return true
        } catch {
            return false
        }
    }

    private static func ensureAlbum() async throws -> PHAssetCollection {
        let options = PHFetchOptions()
        options.predicate = NSPredicate(format: "title = %@", albumName)
        if let existing = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .albumRegular, options: options).firstObject {
            return existing
        }
        var placeholder: PHObjectPlaceholder?
        try await PHPhotoLibrary.shared().performChanges {
            placeholder = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: albumName).placeholderForCreatedAssetCollection
        }
        guard let id = placeholder?.localIdentifier,
              let album = PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [id], options: nil).firstObject
        else { throw AppError("Couldn't create the Resale album.") }
        return album
    }
}
