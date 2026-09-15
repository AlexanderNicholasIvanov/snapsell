import Foundation

/// One physical item, as the UI sees it. The identification fields mirror
/// contracts/item.schema.json so an ItemDto can be rebuilt for /price and
/// /bundle. Photo fields are file names inside PhotoStore.dir (never absolute
/// paths: the app container moves between installs on iOS).
struct Item: Identifiable, Equatable {
    var id: String
    var remoteId: String? = nil
    var name: String
    var brand: String? = nil
    var model: String? = nil
    var category: String
    var condition: Condition
    var attributes: [String] = []
    var searchQuery: String
    var confidence: Double
    var notes: String? = nil
    /// The image shown for this item: the cutout when one exists, else the original.
    var photoFile: String
    var cutoutFile: String? = nil
    var originalPhotoFile: String
    /// Last /price result; nil until priced.
    var quote: PriceQuote? = nil
    /// The price the user settled on; nil until they accept or edit a quote.
    var finalPrice: Double? = nil
    // Listing copy returned by /identify, kept so a single-item listing can be
    // staged without a second round trip. Not part of the contract's Item.
    var listingTitle: String? = nil
    var listingDescription: String? = nil
    var createdAt: Int64
    var updatedAt: Int64

    /// The price a bundle or a row shows: what the user typed, else the suggestion.
    var price: Double? { finalPrice ?? quote?.suggestedPrice }

    /// The contract Item for this entity, as sent to /price and /bundle.
    func toDto() -> ItemDto {
        ItemDto(
            name: name,
            brand: brand.nonBlank,
            model: model.nonBlank,
            category: category,
            condition: condition,
            attributes: attributes,
            searchQuery: searchQuery.isBlank ? name : searchQuery,
            confidence: confidence,
            notes: notes.nonBlank
        )
    }
}

/// Matches contracts/listing.schema.json.
struct Listing: Identifiable, Equatable {
    var id: String
    var remoteId: String? = nil
    var kind: ListingKind
    var itemIds: [String]
    var title: String
    var description: String
    var price: Double
    var bundleDiscount: Double? = nil
    var status: ListingStatus
    var listedAt: Int64? = nil
    var soldAt: Int64? = nil
    var createdAt: Int64
    var updatedAt: Int64
}

struct ListingWithItems: Identifiable, Equatable {
    var listing: Listing
    var items: [Item]
    var id: String { listing.id }

    /// The contract Listing; the future sync API consumes exactly this.
    func toDto() -> ListingDto {
        ListingDto(
            id: listing.id,
            remoteId: listing.remoteId,
            kind: listing.kind,
            itemIds: items.map(\.id),
            title: listing.title,
            description: listing.description,
            price: listing.price,
            bundleDiscount: listing.bundleDiscount,
            status: listing.status,
            listedAt: listing.listedAt.map(Iso8601.format),
            soldAt: listing.soldAt.map(Iso8601.format),
            createdAt: Iso8601.format(listing.createdAt),
            updatedAt: Iso8601.format(listing.updatedAt)
        )
    }
}

extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var nonBlank: String? { isBlank ? nil : self }
}

extension Optional where Wrapped == String {
    var nonBlank: String? { self?.nonBlank }
    var orEmpty: String { self ?? "" }
}
