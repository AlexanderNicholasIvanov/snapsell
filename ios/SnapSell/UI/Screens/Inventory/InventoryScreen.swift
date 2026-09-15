import SwiftUI

struct InventoryScreen: View {
    @Environment(AppContainer.self) private var app
    @Environment(Router.self) private var router
    @Environment(\.snap) private var c

    private var statusByItem: [String: ListingStatus] {
        var map: [String: ListingStatus] = [:]
        // Listings are newest-first; keep the first status seen per item.
        for l in app.inventory.listings { for it in l.items where map[it.id] == nil { map[it.id] = l.listing.status } }
        return map
    }

    var body: some View {
        @Bindable var router = router
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                SnapTopBar("SnapSell", showBack: false, trailing: {
                    iconBox("square.stack.3d.up", "Build a bundle") { router.push(.bundle(itemId: nil)) }
                    iconBox("slider.horizontal.3", "Settings") { router.push(.settings) }
                })
                tabRow
                if router.inventoryTab == 0 { itemsList } else { listingsList }
            }
            Button { router.push(.capture) } label: {
                Image(systemName: "camera.fill").font(.system(size: 26, weight: .bold)).foregroundStyle(c.bg)
                    .frame(width: 64, height: 64).background(c.accent)
                    .shadow(color: .black.opacity(0.25), radius: 12, y: 6)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Capture items")
            .padding(.trailing, 16).padding(.bottom, 22)
        }
        .screenBackground()
        .toolbar(.hidden, for: .navigationBar)
    }

    private func iconBox(_ symbol: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 20, weight: .semibold)).foregroundStyle(c.text).frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private var tabRow: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                tab(0, "Items", app.inventory.items.count)
                tab(1, "Listings", app.inventory.listings.count)
            }
            Rule()
        }
    }

    private func tab(_ i: Int, _ label: String, _ count: Int) -> some View {
        let active = router.inventoryTab == i
        return Button { router.inventoryTab = i } label: {
            VStack(spacing: 0) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(label).snap(SnapType.tabLabel).foregroundStyle(c.text)
                    Text("\(count)").font(.custom("Archivo-Regular", size: 14)).foregroundStyle(c.text.opacity(0.55))
                    Spacer()
                }
                .padding(.horizontal, 16)
                .frame(maxHeight: .infinity)
                Rectangle().fill(active ? c.accent : .clear).frame(height: 3)
            }
            .frame(height: 50)
        }
        .buttonStyle(.plain)
    }

    private var itemsList: some View {
        let rows = app.inventory.items
        let statuses = statusByItem
        return Group {
            if rows.isEmpty {
                emptyState
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(rows) { item in itemRow(item, status: statuses[item.id]) }
                    }
                    .padding(.bottom, 96)
                }
            }
        }
    }

    private func itemRow(_ item: Item, status: ListingStatus?) -> some View {
        Button { router.push(.item(id: item.id)) } label: {
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    StoredTile(file: item.photoFile, size: 60)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(item.name.isBlank ? "Unidentified item" : item.name).snap(SnapType.rowTitle).foregroundStyle(c.text).lineLimit(1)
                        Text(item.condition.label).snap(SnapType.fieldLabel).foregroundStyle(c.text.opacity(0.6))
                        if let status { StatusChip(status: status).padding(.top, 2) }
                    }
                    Spacer(minLength: 8)
                    VStack(alignment: .trailing, spacing: 2) {
                        let shown = item.finalPrice ?? item.quote?.suggestedPrice
                        Text(shown.map(Money.usd) ?? "—").snap(SnapType.rowPrice).foregroundStyle(c.text)
                        MicroLabel(
                            item.finalPrice != nil ? "Final" : item.quote?.suggestedPrice != nil ? "Suggested" : item.quote != nil ? "No comps" : "Not priced"
                        )
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 14)
                Rule(color: c.divider).frame(height: 1)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var listingsList: some View {
        let listings = app.inventory.listings
        return Group {
            if listings.isEmpty {
                emptyState
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(listings) { l in listingRow(l) }
                    }
                    .padding(.bottom, 96)
                }
            }
        }
    }

    private func listingRow(_ l: ListingWithItems) -> some View {
        let n = l.items.count
        var meta = "\(n) item\(n == 1 ? "" : "s")"
        if let first = l.items.first { meta += " · \(first.condition.label)" }
        if n > 1 { meta += " · \(l.listing.kind.label)" }
        return Button { router.push(.handoff(listingId: l.id)) } label: {
            VStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .top, spacing: 12) {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(l.listing.title).snap(SnapType.rowTitle).foregroundStyle(c.text).lineLimit(2).multilineTextAlignment(.leading)
                            Text(meta).snap(SnapType.fieldLabel).foregroundStyle(c.text.opacity(0.6))
                        }
                        Spacer(minLength: 0)
                        Text(Money.usd(l.listing.price)).snap(SnapType.rowPrice).foregroundStyle(c.text)
                    }
                    HStack(spacing: 12) {
                        StatusChip(status: l.listing.status)
                        if l.listing.status == .listed {
                            Button("Mark as sold") { try? app.inventory.updateListingStatus(l.id, status: .sold) }
                                .snapText(SnapType.buttonLabel).foregroundStyle(c.text).frame(height: 36)
                        }
                        Spacer()
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 14)
                Rule(color: c.divider).frame(height: 1)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 0) {
            Image(systemName: "camera").font(.system(size: 26, weight: .semibold)).foregroundStyle(c.text)
                .frame(width: 56, height: 56).overlay(Rectangle().stroke(c.text, lineWidth: SnapMetrics.rule))
            Text("Nothing here yet").font(.custom("Archivo-ExtraBold", size: 23)).foregroundStyle(c.text).padding(.top, 20)
            Text("Point the camera at one item, or at a whole pile. SnapSell separates them out.")
                .snap(SnapType.bodyLarge).foregroundStyle(c.text.opacity(0.75)).padding(.top, 4)
            Spacer()
        }
        .padding(.horizontal, 28).padding(.vertical, 64)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// CutoutTile backed by the photo store.
struct StoredTile: View {
    @Environment(AppContainer.self) private var app
    var file: String?
    var size: CGFloat = 60
    var selected: Bool = false
    @State private var image: UIImage?
    var body: some View {
        CutoutTile(image: image, size: size, selected: selected)
            .task(id: file) {
                guard let file else { image = nil; return }
                let store = app.photoStore
                image = await Task.detached { store.load(file)?.thumbnail(maxEdge: 300) }.value
            }
    }
}

extension UIImage {
    func thumbnail(maxEdge: CGFloat) -> UIImage {
        let longEdge = max(size.width, size.height)
        guard longEdge > maxEdge else { return self }
        return normalized(maxLongEdge: maxEdge)
    }
}
