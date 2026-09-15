import Foundation
import SwiftData

// SwiftData rows. Enums are stored by their wire name so the store reads like
// the contracts; the quote is stored as its JSON. Listing -> item links are an
// ordered id array on the listing (the order matters to the hand-off).

@Model
final class ItemRecord {
    @Attribute(.unique) var id: String
    var remoteId: String?
    var name: String
    var brand: String?
    var model: String?
    var category: String
    var condition: String
    var attributes: [String]
    var searchQuery: String
    var confidence: Double
    var notes: String?
    var photoFile: String
    var cutoutFile: String?
    var originalPhotoFile: String
    var quoteJSON: Data?
    var finalPrice: Double?
    var listingTitle: String?
    var listingDescription: String?
    var createdAt: Int64
    var updatedAt: Int64

    init(_ item: Item) {
        id = item.id
        remoteId = item.remoteId
        name = item.name
        brand = item.brand
        model = item.model
        category = item.category
        condition = item.condition.wire
        attributes = item.attributes
        searchQuery = item.searchQuery
        confidence = item.confidence
        notes = item.notes
        photoFile = item.photoFile
        cutoutFile = item.cutoutFile
        originalPhotoFile = item.originalPhotoFile
        quoteJSON = item.quote.flatMap { try? SnapsellJSON.encode($0) }
        finalPrice = item.finalPrice
        listingTitle = item.listingTitle
        listingDescription = item.listingDescription
        createdAt = item.createdAt
        updatedAt = item.updatedAt
    }

    func apply(_ item: Item) {
        remoteId = item.remoteId
        name = item.name
        brand = item.brand
        model = item.model
        category = item.category
        condition = item.condition.wire
        attributes = item.attributes
        searchQuery = item.searchQuery
        confidence = item.confidence
        notes = item.notes
        photoFile = item.photoFile
        cutoutFile = item.cutoutFile
        originalPhotoFile = item.originalPhotoFile
        quoteJSON = item.quote.flatMap { try? SnapsellJSON.encode($0) }
        finalPrice = item.finalPrice
        listingTitle = item.listingTitle
        listingDescription = item.listingDescription
        createdAt = item.createdAt
        updatedAt = item.updatedAt
    }

    var item: Item {
        Item(
            id: id,
            remoteId: remoteId,
            name: name,
            brand: brand,
            model: model,
            category: category,
            condition: (try? Condition.fromWire(condition)) ?? .good,
            attributes: attributes,
            searchQuery: searchQuery,
            confidence: confidence,
            notes: notes,
            photoFile: photoFile,
            cutoutFile: cutoutFile,
            originalPhotoFile: originalPhotoFile,
            quote: quoteJSON.flatMap { try? SnapsellJSON.decode(PriceQuote.self, from: $0) },
            finalPrice: finalPrice,
            listingTitle: listingTitle,
            listingDescription: listingDescription,
            createdAt: createdAt,
            updatedAt: updatedAt
        )
    }
}

@Model
final class ListingRecord {
    @Attribute(.unique) var id: String
    var remoteId: String?
    var kind: String
    var itemIds: [String]
    var title: String
    var listingDescription: String
    var price: Double
    var bundleDiscount: Double?
    var status: String
    var listedAt: Int64?
    var soldAt: Int64?
    var createdAt: Int64
    var updatedAt: Int64

    init(_ listing: Listing) {
        id = listing.id
        remoteId = listing.remoteId
        kind = listing.kind.wire
        itemIds = listing.itemIds
        title = listing.title
        listingDescription = listing.description
        price = listing.price
        bundleDiscount = listing.bundleDiscount
        status = listing.status.wire
        listedAt = listing.listedAt
        soldAt = listing.soldAt
        createdAt = listing.createdAt
        updatedAt = listing.updatedAt
    }

    var listing: Listing {
        Listing(
            id: id,
            remoteId: remoteId,
            kind: (try? ListingKind.fromWire(kind)) ?? .single,
            itemIds: itemIds,
            title: title,
            description: listingDescription,
            price: price,
            bundleDiscount: bundleDiscount,
            status: (try? ListingStatus.fromWire(status)) ?? .draft,
            listedAt: listedAt,
            soldAt: soldAt,
            createdAt: createdAt,
            updatedAt: updatedAt
        )
    }
}

enum SnapsellStore {
    static let schema = Schema([ItemRecord.self, ListingRecord.self])

    /// Pre-1.0: no migrations yet; a schema change resets the inventory.
    static func container(inMemory: Bool = false) throws -> ModelContainer {
        let config = ModelConfiguration("snapsell", schema: schema, isStoredInMemoryOnly: inMemory)
        return try ModelContainer(for: schema, configurations: [config])
    }
}
