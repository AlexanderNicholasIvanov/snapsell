import SafariServices
import SwiftUI

struct ItemDetailScreen: View {
    let itemId: String
    @Environment(AppContainer.self) private var app
    @Environment(Router.self) private var router
    @Environment(\.snap) private var c
    @State private var priceText = ""
    @State private var priceTouched = false
    @State private var pricing = false
    @State private var error: String?
    @State private var compURL: URL?

    private var item: Item? { app.inventory.item(itemId) }

    var body: some View {
        VStack(spacing: 0) {
            SnapTopBar("Item", onBack: { router.pop() })
            if let item {
                content(item)
                VStack(spacing: 0) {
                    Rule()
                    HStack(spacing: 8) {
                        SnapButton("Add to bundle", kind: .secondary, fullWidth: false) { router.push(.bundle(itemId: item.id)) }
                        SnapButton("List on Marketplace") { createListing(item) }
                    }
                    .padding(16)
                }
                .background(c.bg)
            } else {
                Text("This item no longer exists.").snap(SnapType.body).foregroundStyle(c.text).padding(24)
                Spacer()
            }
        }
        .onAppear { if !priceTouched { priceText = item?.finalPrice.map(Money.plain) ?? "" } }
        .onChange(of: item?.finalPrice) { _, v in if !priceTouched { priceText = v.map(Money.plain) ?? "" } }
        .sheet(item: $compURL) { url in SafariView(url: url).ignoresSafeArea() }
        .snapScreen()
    }

    private func content(_ item: Item) -> some View {
        let quote = item.quote
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                ZStack { Color.white; StoredImage(file: item.photoFile).padding(12) }.frame(height: 190)
                Rule()
                VStack(alignment: .leading, spacing: 0) {
                    Text(item.name.isBlank ? "Unidentified item" : item.name).snap(SnapType.sectionHead).foregroundStyle(c.text).padding(.top, 16)
                    Text([item.brand, item.model, item.condition.label].compactMap { $0 }.joined(separator: " · "))
                        .snap(SnapType.fieldLabel).foregroundStyle(c.text.opacity(0.6))
                    HStack(alignment: .bottom, spacing: 16) {
                        VStack(alignment: .leading, spacing: 2) {
                            MicroLabel("Suggested")
                            Text(quote?.suggestedPrice.map(Money.usd) ?? (quote != nil ? "No comps" : "—")).snap(SnapType.displayPrice).foregroundStyle(c.text)
                        }
                        Spacer(minLength: 0)
                        VStack(alignment: .leading, spacing: 6) {
                            FieldLabel("Final price")
                            HStack(spacing: 4) {
                                Text("$").snap(SnapType.fieldValueBold).foregroundStyle(c.text)
                                TextField(quote?.suggestedPrice.map(Money.plain) ?? "0", text: $priceText)
                                    .keyboardType(.decimalPad).snapText(SnapType.fieldValueBold).foregroundStyle(c.text)
                                    .onChange(of: priceText) { _, t in editPrice(t) }
                            }
                            .padding(.horizontal, 12).frame(width: 140, height: 48)
                            .background(c.surface).overlay(Rectangle().stroke(c.text, lineWidth: SnapMetrics.rule))
                        }
                    }
                    .padding(.top, 20)
                    if let error { ErrorText(error).padding(.top, 8) }
                    if quote == nil {
                        HStack(spacing: 12) {
                            SnapButton("Get a price", kind: .secondary, enabled: !pricing, fullWidth: false) { reprice(item) }
                            if pricing { Spinner() }
                        }
                        .padding(.top, 12)
                    }
                }
                .padding(.horizontal, 16)
                if let quote {
                    VStack(spacing: 0) {
                        Rule()
                        HStack(spacing: 0) {
                            stat("Asking median", quote.askingMedian); VRule()
                            stat("Low", quote.askingLow); VRule()
                            stat("High", quote.askingHigh)
                        }
                        .fixedSize(horizontal: false, vertical: true)
                        Rule(color: c.divider).frame(height: 1)
                    }
                    .padding(.top, 20)
                    VStack(alignment: .leading, spacing: 0) {
                        let cond = item.condition.label.lowercased()
                        Text("From \(quote.compCount) active \(quote.conditionFiltered ? "\(cond) " : "")listings · local factor \(Int(quote.localSaleFactor * 100))%")
                            .font(.custom("Archivo-SemiBold", size: 11.5)).foregroundStyle(c.text.opacity(0.6)).padding(.top, 12)
                        if !quote.conditionFiltered {
                            HStack(spacing: 0) {
                                Rectangle().fill(c.accent).frame(width: 3)
                                Text("No \(cond)-condition comps were found, so these \(quote.compCount) use every condition. Treat the number as rough.")
                                    .snap(SnapType.bodySmall).foregroundStyle(c.text).padding(12)
                            }
                            .fixedSize(horizontal: false, vertical: true)
                            .background(c.surface).padding(.top, 12)
                        }
                        if !quote.comps.isEmpty {
                            MicroLabel("Comparable listings").padding(.top, 24).padding(.bottom, 8)
                            Rule()
                            ForEach(quote.comps) { comp in
                                CompRow(comp: comp) { compURL = URL(string: comp.url) }
                                Rule(color: c.divider).frame(height: 1)
                            }
                        }
                        if let est = quote.estimatedSold {
                            let isLlm = est.source == .llmEstimate
                            VStack(alignment: .leading, spacing: 0) {
                                MicroLabel("Estimated sold range")
                                Text("\(Money.usd(est.low))–\(Money.usd(est.high))").snap(SnapType.soldRange).foregroundStyle(c.text).padding(.top, 4)
                                OutlinedTag(isLlm ? "Estimate, not sales data" : "Source: \(est.source.humanName)").padding(.top, 10)
                                Text(isLlm ? "Modelled down from what sellers are asking. eBay does not publish completed-sale prices through the API."
                                     : (est.rationale ?? "Reported by \(est.source.humanName)."))
                                    .snap(SnapType.bodySmall).foregroundStyle(c.text.opacity(0.8)).padding(.top, 8)
                            }
                            .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                            .overlay(Rectangle().stroke(c.text, lineWidth: SnapMetrics.rule))
                            .padding(.top, 24)
                        }
                    }
                    .padding(.horizontal, 16)
                }
                Spacer(minLength: 24)
            }
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func stat(_ label: String, _ value: Double?) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label.uppercased()).snap(SnapType.statLabel).foregroundStyle(c.text.opacity(0.55))
            Text(value.map(Money.usd) ?? "—").snap(SnapType.statValue).foregroundStyle(c.text)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Typing a final price overrides the suggestion everywhere; clearing the field removes the override.
    private func editPrice(_ text: String) {
        priceTouched = true
        error = nil
        if text.isBlank {
            try? app.inventory.setFinalPrice(itemId: itemId, price: nil)
        } else if let p = Double(text), p >= 0 {
            try? app.inventory.setFinalPrice(itemId: itemId, price: p)
        }
    }

    private func reprice(_ item: Item) {
        pricing = true
        error = nil
        Task {
            switch await app.pricing.price(item: item.toDto(), localSaleFactor: app.settings.localSaleFactor) {
            case .success(let q): try? app.inventory.saveQuote(itemId: itemId, quote: q)
            case .failure(let e): error = e.message
            }
            pricing = false
        }
    }

    private func createListing(_ item: Item) {
        let price = Double(priceText) ?? item.finalPrice ?? item.quote?.suggestedPrice
        guard let price, price >= 0 else { error = "Set a price first."; return }
        do {
            let id = try app.inventory.createListing(
                kind: .single, itemIds: [itemId],
                title: item.listingTitle.nonBlank ?? item.name,
                description: item.listingDescription.nonBlank ?? defaultDescription(item),
                price: price
            )
            router.push(.handoff(listingId: id))
        } catch {
            self.error = AppError.describe(error).message
        }
    }

    private func defaultDescription(_ item: Item) -> String {
        var s = item.name
        if !item.attributes.isEmpty { s += ", " + item.attributes.joined(separator: ", ") }
        s += ". Condition: \(item.condition.label.lowercased())."
        if let notes = item.notes.nonBlank { s += " " + notes }
        s += " Local pickup."
        return s
    }
}

private struct CompRow: View {
    let comp: Comp
    let onTap: () -> Void
    @Environment(\.snap) private var c
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                if let u = comp.imageUrl, let url = URL(string: u) {
                    AsyncImage(url: url) { img in img.resizable().scaledToFill() } placeholder: { c.neutral300 }
                        .frame(width: 46, height: 46).clipped().overlay(Rectangle().stroke(c.text, lineWidth: SnapMetrics.rule))
                } else {
                    c.neutral300.frame(width: 46, height: 46)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(comp.title).font(.custom("Archivo-Regular", size: 12.5)).foregroundStyle(c.text).lineLimit(2).multilineTextAlignment(.leading)
                    if let cond = comp.condition { Text(cond).font(.custom("Archivo-SemiBold", size: 11)).foregroundStyle(c.text.opacity(0.55)) }
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(Money.usd(comp.total)).snap(SnapType.rowTitle).foregroundStyle(c.text)
                    MicroLabel("Total")
                }
                Image(systemName: "arrow.up.right.square").font(.system(size: 15)).foregroundStyle(c.text.opacity(0.7))
            }
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct SafariView: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController { SFSafariViewController(url: url) }
    func updateUIViewController(_ vc: SFSafariViewController, context: Context) {}
}

extension URL: @retroactive Identifiable {
    public var id: String { absoluteString }
}
