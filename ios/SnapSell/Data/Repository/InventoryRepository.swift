import Foundation
import Observation
import SwiftData

/// On-device inventory (SwiftData). Every write stamps updatedAt from `clock`.
/// `items` and `listings` are observable snapshots, newest first, refreshed
/// after every write; views and view models read them and re-render.
@MainActor
@Observable
final class InventoryRepository {
    private(set) var items: [Item] = []
    private(set) var listings: [ListingWithItems] = []

    @ObservationIgnored private let context: ModelContext
    @ObservationIgnored private let clock: Clock

    init(container: ModelContainer, clock: Clock) {
        self.context = container.mainContext
        self.clock = clock
        reload()
    }

    // ---------------------------------------------------------------- reads

    func item(_ id: String) -> Item? { items.first { $0.id == id } }

    func listing(_ id: String) -> ListingWithItems? { listings.first { $0.id == id } }

    /// Items in the order of `ids`; unknown ids are skipped.
    func items(_ ids: [String]) -> [Item] {
        let byId = Dictionary(items.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        return ids.compactMap { byId[$0] }
    }

    // ---------------------------------------------------------------- items

    func saveItem(_ item: Item) throws {
        var stamped = item
        stamped.updatedAt = clock.nowMillis()
        try upsert(stamped)
        try commit()
    }

    func saveItems(_ list: [Item]) throws {
        let now = clock.nowMillis()
        for var item in list {
            item.updatedAt = now
            try upsert(item)
        }
        try commit()
    }

    func deleteItem(_ id: String) throws {
        if let record = try fetchItem(id) { context.delete(record) }
        try commit()
    }

    /// Stores a fresh quote. A final price the user typed is never touched.
    func saveQuote(itemId: String, quote: PriceQuote) throws {
        guard var existing = item(itemId) else { return }
        existing.quote = quote
        try saveItem(existing)
    }

    /// nil clears the override so the suggestion shows again.
    func setFinalPrice(itemId: String, price: Double?) throws {
        guard var existing = item(itemId) else { return }
        existing.finalPrice = price
        try saveItem(existing)
    }

    /// Settings changed the local-sale factor: every stored quote gets a new
    /// suggested_price from its own asking_median, locally. Final prices are
    /// user input and are left alone.
    func recomputeSuggestedPrices(localSaleFactor: Double) throws {
        let now = clock.nowMillis()
        var changed = false
        for var item in items {
            guard var quote = item.quote else { continue }
            quote.suggestedPrice = try SuggestedPrice.recompute(askingMedian: quote.askingMedian, localSaleFactor: localSaleFactor)
            quote.localSaleFactor = localSaleFactor
            if quote != item.quote {
                item.quote = quote
                item.updatedAt = now
                try upsert(item)
                changed = true
            }
        }
        if changed { try commit() }
    }

    // ---------------------------------------------------------------- listings

    /// Creates a draft listing and returns its id.
    func createListing(kind: ListingKind, itemIds: [String], title: String, description: String, price: Double, bundleDiscount: Double? = nil) throws -> String {
        let now = clock.nowMillis()
        let id = Ids.newId()
        let listing = Listing(
            id: id, kind: kind, itemIds: itemIds, title: title, description: description, price: price,
            bundleDiscount: bundleDiscount, status: .draft, createdAt: now, updatedAt: now
        )
        context.insert(ListingRecord(listing))
        try commit()
        return id
    }

    func updateListingStatus(_ id: String, status: ListingStatus) throws {
        guard let record = try fetchListing(id) else { return }
        let now = clock.nowMillis()
        record.status = status.wire
        if status == .listed, record.listedAt == nil { record.listedAt = now }
        if status == .sold, record.soldAt == nil { record.soldAt = now }
        record.updatedAt = now
        try commit()
    }

    // ---------------------------------------------------------------- internals

    private func upsert(_ item: Item) throws {
        if let record = try fetchItem(item.id) {
            record.apply(item)
        } else {
            context.insert(ItemRecord(item))
        }
    }

    private func fetchItem(_ id: String) throws -> ItemRecord? {
        var d = FetchDescriptor<ItemRecord>(predicate: #Predicate { $0.id == id })
        d.fetchLimit = 1
        return try context.fetch(d).first
    }

    private func fetchListing(_ id: String) throws -> ListingRecord? {
        var d = FetchDescriptor<ListingRecord>(predicate: #Predicate { $0.id == id })
        d.fetchLimit = 1
        return try context.fetch(d).first
    }

    private func commit() throws {
        try context.save()
        reload()
    }

    private func reload() {
        let itemRecords = (try? context.fetch(FetchDescriptor<ItemRecord>(sortBy: [SortDescriptor(\.createdAt, order: .reverse)]))) ?? []
        let all = itemRecords.map(\.item)
        let byId = Dictionary(all.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        let listingRecords = (try? context.fetch(FetchDescriptor<ListingRecord>(sortBy: [SortDescriptor(\.createdAt, order: .reverse)]))) ?? []
        items = all
        listings = listingRecords.map { r in
            let l = r.listing
            return ListingWithItems(listing: l, items: l.itemIds.compactMap { byId[$0] })
        }
    }
}
