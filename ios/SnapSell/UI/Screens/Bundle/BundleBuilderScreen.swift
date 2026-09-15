import SwiftUI

struct BundleBuilderScreen: View {
    let initialItemId: String?
    @Environment(AppContainer.self) private var app
    @Environment(Router.self) private var router
    @Environment(\.snap) private var c
    @State private var selected: Set<String> = []
    @State private var discount = BundlePricing.defaultDiscount
    @State private var discountText = "\(Int(BundlePricing.defaultDiscount * 100))"
    @State private var writing = false
    @State private var title = ""
    @State private var description = ""
    @State private var error: String?

    /// Only items with a price can go in a bundle.
    private var candidates: [Item] { app.inventory.items.filter { $0.price != nil } }
    private var selectedItems: [Item] { candidates.filter { selected.contains($0.id) } }
    private var itemPrices: [Double] { selectedItems.compactMap(\.price) }
    private var sum: Double { itemPrices.reduce(0, +) }
    private var bundlePrice: Double { itemPrices.isEmpty ? 0 : ((try? BundlePricing.compute(itemPrices: itemPrices, discount: discount)) ?? 0) }
    private var hasListingText: Bool { !title.isBlank && !description.isBlank }

    var body: some View {
        VStack(spacing: 0) {
            SnapTopBar("Bundle", onBack: { router.pop() })
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    MicroLabel("Priced items").padding(.leading, 16).padding(.top, 16).padding(.bottom, 8)
                    Rule()
                    if candidates.isEmpty {
                        Text("No priced items yet. Confirm and price at least two items first.").snap(SnapType.body).foregroundStyle(c.text.opacity(0.7)).padding(16)
                    }
                    ForEach(candidates) { item in candidateRow(item) }
                    summary.padding(16)
                    VStack(alignment: .leading, spacing: 12) {
                        SnapButton(hasListingText ? "Rewrite listing" : "Write listing", kind: .secondary, enabled: selected.count >= 2 && !writing, loading: writing) { writeListing() }
                        if hasListingText {
                            SnapTextField(label: "Title", text: $title).padding(.top, 4)
                            SnapTextField(label: "Description", text: $description, multiline: true)
                        }
                    }
                    .padding(.horizontal, 16)
                    Spacer(minLength: 24)
                }
            }
            .scrollDismissesKeyboard(.interactively)
            VStack(spacing: 0) {
                Rule()
                SnapButton("List on Marketplace", enabled: hasListingText && selected.count >= 2) { createListing() }.padding(16)
            }
            .background(c.bg)
        }
        .onAppear { if let initialItemId, selected.isEmpty { selected = [initialItemId] } }
        .snapScreen()
    }

    private func candidateRow(_ item: Item) -> some View {
        let on = selected.contains(item.id)
        return Button { toggle(item.id) } label: {
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    SquareCheckbox(checked: on)
                    StoredTile(file: item.photoFile, size: 40)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.name).font(.custom("Archivo-SemiBold", size: 14)).foregroundStyle(c.text).lineLimit(1)
                        Text(item.condition.label).snap(SnapType.fieldLabel).foregroundStyle(c.text.opacity(0.6))
                    }
                    Spacer(minLength: 8)
                    Text(Money.usd(item.price ?? 0)).font(.custom("Archivo-SemiBold", size: 16)).foregroundStyle(c.text)
                }
                .padding(.horizontal, 16).padding(.vertical, 10)
                Rule(color: c.divider).frame(height: 1)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var summary: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Sum of \(selected.count) item\(selected.count == 1 ? "" : "s")").snap(SnapType.bodySmall).foregroundStyle(c.text.opacity(0.75))
                Spacer()
                Text(Money.usd(sum)).snap(SnapType.rowPrice).foregroundStyle(c.text)
            }
            Rule(color: c.divider).frame(height: 1).padding(.vertical, 14)
            HStack(spacing: 6) {
                Text("Bundle discount").snap(SnapType.body).foregroundStyle(c.text)
                Spacer()
                TextField("", text: $discountText).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                    .font(.custom("Archivo-ExtraBold", size: 16)).foregroundStyle(c.text)
                    .frame(width: 66, height: 40).padding(.horizontal, 8)
                    .background(c.surface).overlay(Rectangle().stroke(c.text, lineWidth: SnapMetrics.rule))
                    .onChange(of: discountText) { _, t in setDiscountText(t) }
                Text("%").snap(SnapType.body).foregroundStyle(c.text)
            }
            SnapSlider(value: Binding(get: { discount * 100 }, set: { setDiscountPercent(Int($0.rounded())) }), range: 50...100, step: 1).padding(.top, 8)
            HStack { MicroLabel("50%"); Spacer(); MicroLabel("100%") }
            Rule().padding(.vertical, 14)
            MicroLabel("Bundle price")
            Text(Money.usd(bundlePrice)).snap(SnapType.bundlePrice).foregroundStyle(c.accent700).padding(.top, 2)
            Text("\(Money.usd(sum)) × \(Int(discount * 100))% = \(Money.usd(bundlePrice))")
                .font(.custom("Archivo-SemiBold", size: 11.5)).foregroundStyle(c.text.opacity(0.6)).padding(.top, 4)
            if let error { ErrorText(error).padding(.top, 8) }
        }
        .padding(16)
        .overlay(Rectangle().stroke(c.text, lineWidth: SnapMetrics.rule))
    }

    private func toggle(_ id: String) {
        if selected.contains(id) { selected.remove(id) } else { selected.insert(id) }
        // Any change to the set invalidates the copy the backend wrote.
        title = ""; description = ""; error = nil
    }

    private func setDiscountPercent(_ percent: Int) {
        let d = min(max(Double(percent) / 100, BundlePricing.minDiscount), BundlePricing.maxDiscount)
        guard d != discount else { return }
        discount = d
        discountText = "\(Int(d * 100))"
        title = ""; description = ""
    }

    private func setDiscountText(_ text: String) {
        let digits = String(text.filter(\.isNumber).prefix(3))
        if digits != text { discountText = digits }
        if let p = Int(digits), (50...100).contains(p), Double(p) / 100 != discount {
            discount = Double(p) / 100
            title = ""; description = ""
        }
    }

    private func writeListing() {
        let items = selectedItems
        guard items.count >= 2 else { error = "Pick at least two items."; return }
        writing = true
        error = nil
        Task {
            let pairs = items.map { ($0.toDto(), $0.price ?? 0) }
            switch await app.bundles.writeListing(items: pairs, bundlePrice: bundlePrice) {
            case .success(let r): title = r.title; description = r.description
            case .failure(let e): error = e.message
            }
            writing = false
        }
    }

    private func createListing() {
        let items = selectedItems
        guard hasListingText, items.count >= 2 else { return }
        do {
            let id = try app.inventory.createListing(
                kind: .bundle, itemIds: items.map(\.id),
                title: title.trimmingCharacters(in: .whitespacesAndNewlines),
                description: description.trimmingCharacters(in: .whitespacesAndNewlines),
                price: bundlePrice, bundleDiscount: discount
            )
            router.push(.handoff(listingId: id))
        } catch {
            self.error = AppError.describe(error).message
        }
    }
}
