import SwiftUI

struct ConfirmScreen: View {
    let itemIds: [String]
    @Environment(AppContainer.self) private var app
    @Environment(Router.self) private var router
    @Environment(\.snap) private var c
    @State private var vm: ConfirmViewModel?

    var body: some View {
        Group {
            if let vm { content(vm) } else { Color.clear }
        }
        .onAppear { if vm == nil { vm = ConfirmViewModel(app: app, itemIds: itemIds) } }
        .snapScreen()
    }

    private func content(_ vm: ConfirmViewModel) -> some View {
        let confirmedCount = vm.cards.filter(\.confirmed).count
        let outstanding = vm.cards.filter { $0.identified && !$0.confirmed }.count
        return VStack(spacing: 0) {
            SnapTopBar("Confirm", onBack: { router.pop() }) {
                Text("\(confirmedCount)/\(vm.cards.count) confirmed".uppercased())
                    .font(.custom("Archivo-SemiBold", size: 11)).kerning(1.1).foregroundStyle(c.text.opacity(0.55)).padding(.trailing, 16)
            }
            if vm.loading {
                LoadingBox("Loading").padding(16)
                Spacer()
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        if let e = vm.error { ErrorText(e) }
                        ForEach(vm.cards) { card in
                            ConfirmCardView(card: card, vm: vm)
                                .transition(.opacity.combined(with: .offset(y: 6)))
                        }
                        Text("Nothing is priced until you confirm the identification. Change the name, model or condition afterwards and the price is fetched again.")
                            .snap(SnapType.fieldLabel).foregroundStyle(c.text.opacity(0.55)).textCase(nil)
                    }
                    .padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 24)
                    .animation(.easeOut(duration: 0.22), value: vm.cards.map(\.id))
                }
                .scrollDismissesKeyboard(.interactively)
                VStack(spacing: 0) {
                    Rule()
                    HStack(spacing: 8) {
                        if outstanding > 1 {
                            SnapButton("Confirm all", kind: .secondary, enabled: !vm.anyBusy) { vm.confirmAll() }
                        }
                        SnapButton("Done", enabled: !vm.cards.isEmpty && vm.cards.allSatisfy(\.confirmed) && !vm.anyBusy, fullWidth: outstanding <= 1) {
                            router.goHome()
                        }
                    }
                    .padding(16)
                }
                .background(c.bg)
            }
        }
    }
}

private struct ConfirmCardView: View {
    let card: ConfirmCard
    let vm: ConfirmViewModel
    @Environment(Router.self) private var router
    @Environment(\.snap) private var c

    private func binding(_ get: @escaping (ConfirmCard) -> String, _ set: @escaping (String, String) -> Void) -> Binding<String> {
        Binding(get: { vm.card(card.itemId).map(get) ?? "" }, set: { set(card.itemId, $0) })
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 14) {
                StoredTile(file: card.photoFile, size: 84)
                VStack(alignment: .leading, spacing: 0) {
                    if card.identifying {
                        HStack(spacing: 8) { Spinner(); MicroLabel("Identifying") }
                        Skeleton().padding(.top, 14)
                        Skeleton(width: 140).padding(.top, 8)
                    } else if let err = card.identifyError {
                        Text("Couldn't identify this one").font(.custom("Archivo-ExtraBold", size: 14)).foregroundStyle(c.accent700)
                        Text("The model returned no confident match.").snap(SnapType.bodySmall).foregroundStyle(c.text).padding(.top, 2)
                        Text(err).font(.custom("Archivo-SemiBold", size: 11)).foregroundStyle(c.text.opacity(0.55)).padding(.top, 4)
                        SnapButton("Retry", kind: .secondary, fullWidth: false) { vm.identify(card.itemId) }.padding(.top, 10)
                    } else {
                        SnapTextField(label: "Name", text: binding({ $0.name }, vm.editName), placeholder: "What is it?")
                    }
                }
            }
            .padding(14)
            if card.identified, !card.identifying {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 10) {
                        SnapTextField(label: "Brand", text: binding({ $0.brand }, vm.editBrand))
                        SnapTextField(label: "Model", text: binding({ $0.model }, vm.editModel))
                    }
                    VStack(alignment: .leading, spacing: 6) {
                        FieldLabel("Condition")
                        ConditionChipRow(selected: Binding(get: { vm.card(card.itemId)?.condition ?? .good }, set: { vm.editCondition(card.itemId, $0) }))
                    }
                    SnapTextField(label: "Notes", text: binding({ $0.notes }, vm.editNotes), placeholder: "Scratches, missing parts, pickup details", multiline: true)
                }
                .padding(.horizontal, 14).padding(.bottom, 14)
                Rule()
                if card.confirmed {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            MicroLabel("Suggested price")
                            if card.pricing {
                                HStack(spacing: 8) { Spinner(); MicroLabel(card.quote == nil ? "Pricing" : "Re-pricing") }.padding(.top, 6)
                            } else if let q = card.quote {
                                Text(q.suggestedPrice.map(Money.usd) ?? "No comps").snap(SnapType.cardPrice).foregroundStyle(c.text)
                            } else {
                                ErrorText(card.priceError ?? "Not priced")
                            }
                        }
                        Spacer()
                        if !card.pricing {
                            if card.quote != nil {
                                SnapButton("Detail", kind: .secondary, systemImage: "chevron.right", fullWidth: false) { router.push(.item(id: card.itemId)) }
                            } else {
                                SnapButton("Retry", kind: .secondary, fullWidth: false) { vm.confirm(card.itemId) }
                            }
                        }
                    }
                    .padding(14)
                } else {
                    HStack(spacing: 8) {
                        SnapButton("Remove", kind: .secondary, fullWidth: false) { vm.remove(card.itemId) }
                        SnapButton("Confirm", enabled: !card.busy) { vm.confirm(card.itemId) }
                    }
                    .padding(14)
                }
            } else if card.identifyError != nil {
                Rule()
                HStack { SnapButton("Remove", kind: .secondary, fullWidth: false) { vm.remove(card.itemId) } }.padding(14)
            }
        }
        .background(c.surface)
        .overlay(Rectangle().stroke(c.divider, lineWidth: 1))
    }
}
