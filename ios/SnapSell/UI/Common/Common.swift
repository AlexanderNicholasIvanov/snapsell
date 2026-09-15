import SwiftUI

// Shared Modernist components: square corners, 2pt rules, flush-left labels.

struct Rule: View {
    @Environment(\.snap) private var c
    var color: Color? = nil
    var body: some View {
        Rectangle().fill(color ?? c.text).frame(height: SnapMetrics.rule)
    }
}

struct VRule: View {
    @Environment(\.snap) private var c
    var body: some View {
        Rectangle().fill(c.text).frame(width: SnapMetrics.rule)
    }
}

struct SnapTopBar<Trailing: View>: View {
    @Environment(\.snap) private var c
    @Environment(\.dismiss) private var dismiss
    var title: String
    var showBack: Bool = true
    var onBack: (() -> Void)? = nil
    @ViewBuilder var trailing: () -> Trailing

    init(_ title: String, showBack: Bool = true, onBack: (() -> Void)? = nil, @ViewBuilder trailing: @escaping () -> Trailing = { EmptyView() }) {
        self.title = title
        self.showBack = showBack
        self.onBack = onBack
        self.trailing = trailing
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                if showBack {
                    Button { onBack?() ?? dismiss() } label: {
                        Image(systemName: "arrow.left").font(.system(size: 20, weight: .bold))
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Back")
                }
                Text(title).snap(SnapType.appBarTitle).lineLimit(1)
                Spacer()
                trailing()
            }
            .foregroundStyle(c.text)
            .padding(.horizontal, showBack ? 8 : SnapMetrics.gutter)
            .frame(height: 56)
            Rule()
        }
        .background(c.bg)
    }
}

struct MicroLabel: View {
    @Environment(\.snap) private var c
    var text: String
    var color: Color? = nil
    init(_ text: String, color: Color? = nil) { self.text = text; self.color = color }
    var body: some View {
        Text(text.uppercased()).snap(SnapType.microLabel).foregroundStyle(color ?? c.neutral500)
    }
}

struct FieldLabel: View {
    @Environment(\.snap) private var c
    var text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text.uppercased()).snap(SnapType.fieldLabel).foregroundStyle(c.neutral800)
    }
}

struct SectionHead: View {
    @Environment(\.snap) private var c
    var text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text).snap(SnapType.sectionHead).foregroundStyle(c.text)
    }
}

enum SnapButtonKind { case primary, secondary, ghost, danger }

struct SnapButton: View {
    @Environment(\.snap) private var c
    var title: String
    var kind: SnapButtonKind = .primary
    var systemImage: String? = nil
    var enabled: Bool = true
    var loading: Bool = false
    var fullWidth: Bool = true
    var action: () -> Void

    init(_ title: String, kind: SnapButtonKind = .primary, systemImage: String? = nil, enabled: Bool = true, loading: Bool = false, fullWidth: Bool = true, action: @escaping () -> Void) {
        self.title = title; self.kind = kind; self.systemImage = systemImage
        self.enabled = enabled; self.loading = loading; self.fullWidth = fullWidth; self.action = action
    }

    private var isOn: Bool { enabled && !loading }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if loading {
                    ProgressView().tint(foreground).controlSize(.small)
                } else if let systemImage {
                    Image(systemName: systemImage).font(.system(size: 15, weight: .bold))
                }
                Text(title).snap(SnapType.buttonLabel)
                if fullWidth { Spacer(minLength: 0) }
            }
            .foregroundStyle(foreground)
            .padding(.horizontal, 16)
            .frame(maxWidth: fullWidth ? .infinity : nil, minHeight: SnapMetrics.controlHeight, alignment: .leading)
            .background(background)
            .overlay(Rectangle().stroke(border, lineWidth: SnapMetrics.rule))
            .opacity(isOn ? 1 : 0.5)
        }
        .buttonStyle(.plain)
        .disabled(!isOn)
    }

    private var foreground: Color {
        switch kind {
        case .primary: return c.onAccent
        case .secondary, .ghost: return c.text
        case .danger: return c.accent
        }
    }
    private var background: Color {
        switch kind {
        case .primary: return c.accent
        case .secondary: return c.surface
        case .ghost, .danger: return .clear
        }
    }
    private var border: Color {
        switch kind {
        case .primary: return c.accent
        case .secondary: return c.text
        case .ghost: return .clear
        case .danger: return c.accent
        }
    }
}

struct StatusChip: View {
    @Environment(\.snap) private var c
    var status: ListingStatus
    var body: some View {
        Text(status.label.uppercased()).snap(SnapType.tag)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .foregroundStyle(fg)
            .background(bg)
    }
    private var fg: Color {
        switch status {
        case .draft: return c.neutral800
        case .listed: return c.accent800
        case .sold: return c.onAccent
        case .skipped: return c.neutral500
        }
    }
    private var bg: Color {
        switch status {
        case .draft: return c.neutral300
        case .listed: return c.accent100
        case .sold: return c.accent
        case .skipped: return c.neutral100
        }
    }
}

struct OutlinedTag: View {
    @Environment(\.snap) private var c
    var text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text.uppercased()).snap(SnapType.tag).foregroundStyle(c.text)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .overlay(Rectangle().stroke(c.text, lineWidth: 1.5))
    }
}

struct ConditionChipRow: View {
    @Environment(\.snap) private var c
    @Binding var selected: Condition
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Condition.allCases) { cond in
                    let on = cond == selected
                    Button { selected = cond } label: {
                        Text(cond.label).snap(SnapType.chipLabel)
                            .padding(.horizontal, 12).frame(height: 36)
                            .foregroundStyle(on ? c.onAccent : c.text)
                            .background(on ? c.accent : c.surface)
                            .overlay(Rectangle().stroke(on ? c.accent : c.neutral300, lineWidth: SnapMetrics.rule))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

struct SnapTextField: View {
    @Environment(\.snap) private var c
    var label: String
    @Binding var text: String
    var placeholder: String = ""
    var keyboard: UIKeyboardType = .default
    var multiline: Bool = false
    var error: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            FieldLabel(label)
            Group {
                if multiline {
                    TextField(placeholder, text: $text, axis: .vertical).lineLimit(3...10)
                } else {
                    TextField(placeholder, text: $text)
                }
            }
            .snapText(SnapType.fieldValue)
            .keyboardType(keyboard)
            .autocorrectionDisabled(keyboard != .default)
            .foregroundStyle(c.text)
            .padding(.horizontal, 12)
            .padding(.vertical, multiline ? 12 : 0)
            .frame(minHeight: SnapMetrics.controlHeight, alignment: .leading)
            .background(c.surface)
            .overlay(Rectangle().stroke(error == nil ? c.text : c.accent, lineWidth: SnapMetrics.rule))
            if let error {
                ErrorText(error)
            }
        }
    }
}

struct SquareCheckbox: View {
    @Environment(\.snap) private var c
    var checked: Bool
    var body: some View {
        ZStack {
            Rectangle().fill(checked ? c.accent : c.surface)
            Rectangle().stroke(checked ? c.accent : c.text, lineWidth: SnapMetrics.rule)
            if checked { Image(systemName: "checkmark").font(.system(size: 13, weight: .heavy)).foregroundStyle(c.onAccent) }
        }
        .frame(width: 24, height: 24)
    }
}

struct Skeleton: View {
    @Environment(\.snap) private var c
    var height: CGFloat = 16
    var width: CGFloat? = nil
    @State private var on = false
    var body: some View {
        Rectangle().fill(c.neutral300).frame(width: width, height: height)
            .opacity(on ? 0.45 : 1)
            .animation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true), value: on)
            .onAppear { on = true }
    }
}

struct Spinner: View {
    @Environment(\.snap) private var c
    var body: some View { ProgressView().tint(c.accent) }
}

struct LoadingBox: View {
    @Environment(\.snap) private var c
    var text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        HStack(spacing: 12) {
            Spinner()
            Text(text).snap(SnapType.body).foregroundStyle(c.neutral800)
            Spacer()
        }
        .padding(16)
        .background(c.surface)
    }
}

struct ErrorText: View {
    @Environment(\.snap) private var c
    var text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text).snap(SnapType.bodySmall).foregroundStyle(c.accent).fixedSize(horizontal: false, vertical: true)
    }
}

struct ErrorBox: View {
    @Environment(\.snap) private var c
    var text: String
    var retry: (() -> Void)? = nil
    init(_ text: String, retry: (() -> Void)? = nil) { self.text = text; self.retry = retry }
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ErrorText(text)
            if let retry { SnapButton("Retry", kind: .secondary, fullWidth: false, action: retry) }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(c.accent100)
    }
}

struct SnapSlider: View {
    @Environment(\.snap) private var c
    @Binding var value: Double
    var range: ClosedRange<Double>
    var step: Double
    var body: some View {
        Slider(value: $value, in: range, step: step).tint(c.accent)
    }
}

/// A square image tile with a 2pt frame, for cutouts and item thumbnails.
struct CutoutTile: View {
    @Environment(\.snap) private var c
    var image: UIImage?
    var size: CGFloat = 72
    var selected: Bool = false
    var body: some View {
        ZStack {
            Rectangle().fill(.white)
            if let image {
                Image(uiImage: image).resizable().scaledToFit().padding(2)
            } else {
                Image(systemName: "photo").foregroundStyle(c.neutral500)
            }
        }
        .frame(width: size, height: size)
        .overlay(Rectangle().stroke(selected ? c.accent : c.text, lineWidth: selected ? 3 : SnapMetrics.rule))
    }
}

struct Scrim: View {
    var body: some View { Color.black.opacity(0.35).ignoresSafeArea() }
}

/// Stat cell for the inventory header.
struct StatCell: View {
    @Environment(\.snap) private var c
    var label: String
    var value: String
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).snap(SnapType.statValue).foregroundStyle(c.text)
            Text(label.uppercased()).snap(SnapType.statLabel).foregroundStyle(c.neutral500)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Async photo-store image.
struct StoredImage: View {
    @Environment(AppContainer.self) private var app
    var file: String?
    @State private var image: UIImage?
    var body: some View {
        Group {
            if let image { Image(uiImage: image).resizable().scaledToFit() } else { Color.white }
        }
        .task(id: file) {
            guard let file else { image = nil; return }
            let store = app.photoStore
            image = await Task.detached { store.load(file) }.value
        }
    }
}

extension View {
    func screenBackground() -> some View {
        modifier(ScreenBackground())
    }
}

private struct ScreenBackground: ViewModifier {
    @Environment(\.snap) private var c
    func body(content: Content) -> some View {
        content.frame(maxWidth: .infinity, maxHeight: .infinity).background(c.bg.ignoresSafeArea())
    }
}
