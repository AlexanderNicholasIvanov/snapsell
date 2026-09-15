import Foundation

protocol IdentifyRepository {
    /// Identify the item shown in `imageURL` (a JPEG cutout or photo).
    func identify(imageURL: URL, hint: String?) async -> Result<IdentifyResponse, AppError>
}

protocol PricingRepository {
    func price(item: ItemDto, localSaleFactor: Double) async -> Result<PriceQuote, AppError>
}

protocol BundleRepository {
    /// Ask the backend to write title + description for a bundle priced on-device.
    func writeListing(items: [(ItemDto, Double)], bundlePrice: Double) async -> Result<BundleResponse, AppError>
}

final class RemoteIdentifyRepository: IdentifyRepository {
    private let api: SnapsellApi
    init(api: SnapsellApi) { self.api = api }

    func identify(imageURL: URL, hint: String?) async -> Result<IdentifyResponse, AppError> {
        await appResult {
            let bytes = try Data(contentsOf: imageURL)
            // Standard alphabet, no newlines, per the contract.
            let encoded = bytes.base64EncodedString()
            return try await api.identify(IdentifyRequest(imageBase64: encoded, mediaType: IdentifyRequest.mediaTypeJPEG, hint: hint))
        }
    }
}

final class RemotePricingRepository: PricingRepository {
    private let api: SnapsellApi
    init(api: SnapsellApi) { self.api = api }

    func price(item: ItemDto, localSaleFactor: Double) async -> Result<PriceQuote, AppError> {
        await appResult { try await api.price(PriceRequest(item: item, localSaleFactor: localSaleFactor)) }
    }
}

final class RemoteBundleRepository: BundleRepository {
    private let api: SnapsellApi
    init(api: SnapsellApi) { self.api = api }

    func writeListing(items: [(ItemDto, Double)], bundlePrice: Double) async -> Result<BundleResponse, AppError> {
        await appResult {
            try await api.bundle(BundleRequest(items: items.map { BundleRequestItem(item: $0.0, price: $0.1) }, bundlePrice: bundlePrice))
        }
    }
}
