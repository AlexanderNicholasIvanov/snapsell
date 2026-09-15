import XCTest
@testable import SnapSell

@MainActor
final class InventoryRepositoryTests: XCTestCase {
    private func blank(_ now: Int64) -> Item {
        Item(
            id: Ids.newId(),
            name: "", category: "", condition: .good, searchQuery: "", confidence: 0,
            photoFile: "p.jpg", cutoutFile: "c.png", originalPhotoFile: "o.jpg",
            createdAt: now, updatedAt: now
        )
    }

    func testSaveItemsBatchThenUpdate() throws {
        let repo = InventoryRepository(container: try SnapsellStore.container(inMemory: true), clock: SystemClock())
        let now: Int64 = 1_000
        let batch = [blank(now), blank(now), blank(now)]
        try repo.saveItems(batch)
        XCTAssertEqual(Set(repo.items.map(\.id)), Set(batch.map(\.id)))

        var renamed = batch[1]
        renamed.name = "MacBook Air"
        try repo.saveItems([renamed, batch[0]])
        XCTAssertEqual(repo.items.count, 3)
        XCTAssertEqual(repo.item(batch[1].id)?.name, "MacBook Air")

        try repo.deleteItem(batch[2].id)
        XCTAssertEqual(repo.items.count, 2)
    }
}
