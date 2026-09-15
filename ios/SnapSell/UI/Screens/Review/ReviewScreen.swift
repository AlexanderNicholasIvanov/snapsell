import SwiftUI

/// Where the photo lands inside its box (fit, centred).
private struct Fit {
    var scale: CGFloat, dx: CGFloat, dy: CGFloat
    func toImage(_ p: CGPoint) -> CGPoint { CGPoint(x: (p.x - dx) / scale, y: (p.y - dy) / scale) }
    func toView(_ r: CGRect) -> CGRect { CGRect(x: dx + r.minX * scale, y: dy + r.minY * scale, width: r.width * scale, height: r.height * scale) }
    static func into(_ image: CGSize, box: CGSize) -> Fit {
        let s = min(box.width / image.width, box.height / image.height)
        return Fit(scale: s, dx: (box.width - image.width * s) / 2, dy: (box.height - image.height * s) / 2)
    }
}

struct ReviewScreen: View {
    @Environment(AppContainer.self) private var app
    @Environment(Router.self) private var router
    @Environment(\.snap) private var c
    @State private var vm: ReviewViewModel?
    // Box mode: the dragged rectangle stays as a draft until "Place box" commits it.
    @State private var dragRect: CGRect?
    @State private var draft: CGRect?

    var body: some View {
        Group {
            if let vm { content(vm) } else { Color.clear }
        }
        .onAppear { if vm == nil { vm = ReviewViewModel(app: app) } }
        .snapScreen()
    }

    private func retake() { router.replaceTop(with: .capture) }

    @ViewBuilder
    private func content(_ vm: ReviewViewModel) -> some View {
        @Bindable var vm = vm
        VStack(spacing: 0) {
            SnapTopBar("Review items", onBack: { router.pop() })
            if let image = vm.image {
                if vm.committing {
                    LoadingBox("Cutting out \(vm.selectedCount) object(s)").padding(16)
                    Spacer()
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 0) {
                            if vm.outlinerUnavailable {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("On-device outlining unavailable").font(.custom("Archivo-ExtraBold", size: 13)).foregroundStyle(c.accent700)
                                    Text("This device can't run the object outliner. Draw a box around each thing you want to sell.").snap(SnapType.bodySmall).foregroundStyle(c.text)
                                }
                                .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                                .overlay(Rectangle().stroke(c.accent, lineWidth: SnapMetrics.rule))
                                .padding(.horizontal, 16).padding(.top, 14)
                            }
                            photoBox(vm, image)
                            Text("\(vm.segments.count) object\(vm.segments.count == 1 ? "" : "s") found")
                                .font(.custom("Archivo-SemiBold", size: 11)).foregroundStyle(c.text.opacity(0.5)).padding(.horizontal, 16)
                            VStack(alignment: .leading, spacing: 12) {
                                if let e = vm.error { ErrorText(e) }
                                if !vm.segments.isEmpty { chips(vm) }
                                HStack(spacing: 8) {
                                    if vm.manualMode {
                                        SnapButton("Place box", kind: .secondary, enabled: draft != nil, fullWidth: false) {
                                            if let d = draft { vm.addManualRect(d) }
                                            draft = nil; dragRect = nil
                                        }
                                    } else {
                                        SnapButton("Add item", kind: .secondary) { draft = nil; dragRect = nil; vm.manualMode = true }
                                    }
                                    SnapButton("Retake photo", kind: .secondary) { retake() }
                                }
                                if vm.manualMode, !vm.segments.isEmpty {
                                    SnapButton("Cancel box", kind: .ghost, fullWidth: false) { draft = nil; dragRect = nil; vm.manualMode = false }
                                }
                            }
                            .padding(16)
                        }
                    }
                    bottomBar(vm)
                }
            } else {
                VStack(alignment: .leading, spacing: 16) {
                    ErrorText(vm.error ?? "No photo.")
                    SnapButton("Take a photo") { retake() }
                }
                .padding(24)
                Spacer()
            }
        }
    }

    private func bottomBar(_ vm: ReviewViewModel) -> some View {
        let n = vm.selectedCount
        return VStack(spacing: 0) {
            Rule()
            SnapButton(n == 0 ? "Select at least one object" : "Identify \(n) item\(n == 1 ? "" : "s")", enabled: n > 0 && !vm.segmenting) {
                vm.identify { ids in router.toConfirm(ids) }
            }
            .padding(16)
        }
        .background(c.bg)
    }

    private func photoBox(_ vm: ReviewViewModel, _ image: UIImage) -> some View {
        let ratio = image.size.width / image.size.height
        return GeometryReader { geo in
            let w = geo.size.width
            let h = min(w / ratio, 560)
            let fit = Fit.into(image.size, box: CGSize(width: w, height: h))
            ZStack(alignment: .topLeading) {
                Color(hex: 0x111010)
                Image(uiImage: image).resizable().frame(width: image.size.width * fit.scale, height: image.size.height * fit.scale)
                    .offset(x: fit.dx, y: fit.dy)
                if vm.manualMode { Color.black.opacity(0.5) }
                ForEach(vm.segments) { seg in
                    let on = vm.selected.contains(seg.index)
                    let r = fit.toView(seg.bounds)
                    Rectangle().stroke(on ? c.accent : .white.opacity(0.75), style: StrokeStyle(lineWidth: on ? 2.5 : 2, dash: on ? [] : [8, 5]))
                        .frame(width: r.width, height: r.height).offset(x: r.minX, y: r.minY)
                    Text("\(seg.index + 1)").font(.custom("Archivo-ExtraBold", size: 11))
                        .foregroundStyle(on ? c.bg : c.text)
                        .frame(width: 22, height: 22).background(on ? c.accent : .white)
                        .offset(x: r.minX, y: r.minY)
                }
                if let d = dragRect ?? draft {
                    let r = fit.toView(d)
                    Rectangle().stroke(.white, style: StrokeStyle(lineWidth: 2, dash: [8, 5]))
                        .frame(width: r.width, height: r.height).offset(x: r.minX, y: r.minY)
                }
                if vm.manualMode, draft == nil, dragRect == nil {
                    Text("Drag a box around the object").font(.custom("Archivo-ExtraBold", size: 14)).foregroundStyle(.white)
                        .frame(width: w, height: h)
                }
                if vm.segmenting {
                    HStack(spacing: 8) { ProgressView().tint(.white); Text("Finding objects").snap(SnapType.bodySmall).foregroundStyle(.white) }
                        .frame(width: w, height: h).background(Color.black.opacity(0.35))
                }
            }
            .frame(width: w, height: h)
            .clipped()
            .contentShape(Rectangle())
            .overlay(Rectangle().stroke(c.divider, lineWidth: 1))
            .gesture(gesture(vm, fit))
        }
        .aspectRatio(max(ratio, 1 / (560 / UIScreen.main.bounds.width)), contentMode: .fit)
        .padding(.horizontal, 16).padding(.vertical, 14)
    }

    private func gesture(_ vm: ReviewViewModel, _ fit: Fit) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { g in
                guard vm.manualMode else { return }
                let a = fit.toImage(g.startLocation), b = fit.toImage(g.location)
                dragRect = CGRect(x: min(a.x, b.x), y: min(a.y, b.y), width: abs(a.x - b.x), height: abs(a.y - b.y))
            }
            .onEnded { g in
                if vm.manualMode {
                    if let r = dragRect, r.width > 4, r.height > 4 { draft = r }
                    dragRect = nil
                } else if hypot(g.translation.width, g.translation.height) < 10 {
                    vm.tap(at: fit.toImage(g.location))
                }
            }
    }

    private func chips(_ vm: ReviewViewModel) -> some View {
        FlowLayout(spacing: 8) {
            ForEach(vm.segments) { seg in
                let on = vm.selected.contains(seg.index)
                let fg = on ? c.bg : c.text
                HStack(spacing: 0) {
                    Button { vm.toggle(seg.index) } label: {
                        Text("Object \(seg.index + 1)").font(.custom("Archivo-SemiBold", size: 13)).foregroundStyle(fg)
                            .padding(.horizontal, 14).frame(height: 44)
                    }
                    .buttonStyle(.plain)
                    Rectangle().fill(on ? c.bg.opacity(0.5) : c.divider).frame(width: 2, height: 44)
                    Button { vm.retake(seg.index) { retake() } } label: {
                        Image(systemName: "arrow.clockwise").font(.system(size: 15, weight: .bold)).foregroundStyle(fg).frame(width: 40, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Retake object \(seg.index + 1)")
                }
                .background(on ? c.accent : .clear)
                .overlay(Rectangle().stroke(on ? c.accent : c.divider, lineWidth: SnapMetrics.rule))
            }
        }
    }
}

/// Minimal wrapping HStack.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x > 0, x + sz.width > width { x = 0; y += rowH + spacing; rowH = 0 }
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
        }
        return CGSize(width: width == .infinity ? x : width, height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x > 0, x + sz.width > bounds.width { x = 0; y += rowH + spacing; rowH = 0 }
            s.place(at: CGPoint(x: bounds.minX + x, y: bounds.minY + y), proposal: ProposedViewSize(sz))
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
        }
    }
}
