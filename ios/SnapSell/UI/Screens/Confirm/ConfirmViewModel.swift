import Foundation
import Observation

struct ConfirmCard: Identifiable {
    var itemId: String
    var photoFile: String
    var name = ""
    var brand = ""
    var model = ""
    var category = ""
    var condition: Condition = .good
    var notes = ""
    var attributes: [String] = []
    var searchQuery = ""
    var confidence = 0.0
    var identifying = false
    var identifyError: String?
    var pricing = false
    var priceError: String?
    var quote: PriceQuote?
    /// True after the user confirmed the identity at least once.
    var confirmed = false

    var id: String { itemId }
    var identified: Bool { !name.isBlank }
    var busy: Bool { identifying || pricing }
}

/// Identifies each placeholder item (created by Review), lets the user edit
/// identity + condition, and prices on confirm. Editing name/model/condition
/// after a price exists re-runs /price after a short debounce.
@MainActor
@Observable
final class ConfirmViewModel {
    static let repriceDebounceMs: UInt64 = 1200

    private(set) var loading = true
    private(set) var cards: [ConfirmCard] = []
    private(set) var error: String?

    var allPriced: Bool { !cards.isEmpty && cards.allSatisfy { $0.quote != nil } }
    var anyBusy: Bool { cards.contains { $0.busy } }

    @ObservationIgnored private let app: AppContainer
    /// Backend calls are heavy (vision LLM); keep at most two in flight.
    @ObservationIgnored private let identifyLimiter = AsyncSemaphore(limit: 2)
    @ObservationIgnored private var repriceTasks: [String: Task<Void, Never>] = [:]

    init(app: AppContainer, itemIds: [String]) {
        self.app = app
        let items = app.inventory.items(itemIds)
        cards = items.map(Self.card)
        loading = false
        error = items.isEmpty ? "Nothing to confirm." : nil
        for item in items where item.name.isBlank { identify(item.id) }
    }

    private static func card(_ item: Item) -> ConfirmCard {
        ConfirmCard(
            itemId: item.id, photoFile: item.photoFile, name: item.name, brand: item.brand.orEmpty, model: item.model.orEmpty,
            category: item.category, condition: item.condition, notes: item.notes.orEmpty, attributes: item.attributes,
            searchQuery: item.searchQuery, confidence: item.confidence, quote: item.quote, confirmed: item.quote != nil
        )
    }

    private func update(_ id: String, _ transform: (inout ConfirmCard) -> Void) {
        guard let i = cards.firstIndex(where: { $0.itemId == id }) else { return }
        transform(&cards[i])
    }

    func card(_ id: String) -> ConfirmCard? { cards.first { $0.itemId == id } }

    func identify(_ id: String) {
        update(id) { $0.identifying = true; $0.identifyError = nil }
        Task {
            guard let card = card(id) else { return }
            await identifyLimiter.acquire()
            let result = await app.identify.identify(imageURL: app.photoStore.url(card.photoFile), hint: nil)
            identifyLimiter.release()
            switch result {
            case .success(let r):
                if var existing = app.inventory.item(id) {
                    existing.name = r.item.name
                    existing.brand = r.item.brand
                    existing.model = r.item.model
                    existing.category = r.item.category
                    existing.condition = r.item.condition
                    existing.attributes = r.item.attributes
                    existing.searchQuery = r.item.searchQuery
                    existing.confidence = r.item.confidence
                    existing.notes = r.item.notes
                    existing.listingTitle = r.listingText.title
                    existing.listingDescription = r.listingText.description
                    try? app.inventory.saveItem(existing)
                }
                update(id) {
                    $0.identifying = false
                    $0.name = r.item.name; $0.brand = r.item.brand.orEmpty; $0.model = r.item.model.orEmpty
                    $0.category = r.item.category; $0.condition = r.item.condition; $0.notes = r.item.notes.orEmpty
                    $0.attributes = r.item.attributes; $0.searchQuery = r.item.searchQuery; $0.confidence = r.item.confidence
                }
            case .failure(let e):
                update(id) { $0.identifying = false; $0.identifyError = e.message }
            }
        }
    }

    func editName(_ id: String, _ v: String) { edit(id, reprice: true) { $0.name = v } }
    func editBrand(_ id: String, _ v: String) { edit(id, reprice: false) { $0.brand = v } }
    func editModel(_ id: String, _ v: String) { edit(id, reprice: true) { $0.model = v } }
    func editNotes(_ id: String, _ v: String) { edit(id, reprice: false) { $0.notes = v } }
    func editCondition(_ id: String, _ v: Condition) { edit(id, reprice: true) { $0.condition = v } }

    private func edit(_ id: String, reprice: Bool, _ transform: (inout ConfirmCard) -> Void) {
        update(id, transform)
        guard let card = card(id) else { return }
        if reprice, card.quote != nil { scheduleReprice(id) }
    }

    private func scheduleReprice(_ id: String) {
        repriceTasks[id]?.cancel()
        repriceTasks[id] = Task {
            try? await Task.sleep(nanoseconds: Self.repriceDebounceMs * 1_000_000)
            guard !Task.isCancelled else { return }
            confirm(id)
        }
    }

    /// Persist edits and fetch a price.
    func confirm(_ id: String) {
        guard let card = card(id), card.identified, !card.pricing else { return }
        repriceTasks.removeValue(forKey: id)?.cancel()
        update(id) { $0.pricing = true; $0.priceError = nil; $0.confirmed = true }
        Task {
            guard var updated = app.inventory.item(id) else { return }
            let name = card.name.trimmingCharacters(in: .whitespacesAndNewlines)
            let existingName = updated.name
            updated.name = name
            updated.brand = card.brand.trimmingCharacters(in: .whitespacesAndNewlines).nonBlank
            updated.model = card.model.trimmingCharacters(in: .whitespacesAndNewlines).nonBlank
            updated.condition = card.condition
            updated.notes = card.notes.trimmingCharacters(in: .whitespacesAndNewlines).nonBlank
            updated.category = card.category.isBlank ? "Uncategorized" : card.category
            // Keep the model's query unless the user changed the name; then the name is the better query.
            updated.searchQuery = name != existingName ? name : (updated.searchQuery.isBlank ? name : updated.searchQuery)
            try? app.inventory.saveItem(updated)
            let factor = app.settings.localSaleFactor
            switch await app.pricing.price(item: updated.toDto(), localSaleFactor: factor) {
            case .success(let q):
                try? app.inventory.saveQuote(itemId: id, quote: q)
                update(id) { $0.pricing = false; $0.quote = q; $0.searchQuery = updated.searchQuery }
            case .failure(let e):
                update(id) { $0.pricing = false; $0.priceError = e.message }
            }
        }
    }

    func confirmAll() {
        for card in cards where card.identified && !card.pricing { confirm(card.itemId) }
    }

    func remove(_ id: String) {
        repriceTasks.removeValue(forKey: id)?.cancel()
        try? app.inventory.deleteItem(id)
        cards.removeAll { $0.itemId == id }
    }
}

/// Counting semaphore for async code.
actor AsyncSemaphore {
    private let limit: Int
    private var inUse = 0
    private var waiters: [CheckedContinuation<Void, Never>] = []

    init(limit: Int) { self.limit = limit }

    func acquire() async {
        if inUse < limit { inUse += 1; return }
        await withCheckedContinuation { waiters.append($0) }
    }

    nonisolated func release() { Task { await self.releaseInner() } }

    private func releaseInner() {
        if let next = waiters.first { waiters.removeFirst(); next.resume() } else { inUse -= 1 }
    }
}
