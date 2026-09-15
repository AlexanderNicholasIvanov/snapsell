import SwiftUI

/// Modernist palette from docs/design-handoff.md. Light/dark via the
/// environment colour scheme; look these up through `@Environment(\.snap)`.
struct SnapColors {
    var bg: Color
    var surface: Color
    var text: Color
    var accent: Color
    var accent600: Color
    var accent700: Color
    var accent100: Color
    var accent800: Color
    var neutral100: Color
    var neutral300: Color
    var neutral500: Color
    var neutral800: Color
    var divider: Color
    var onAccent: Color { .white }

    static let light = SnapColors(
        bg: Color(hex: 0xF3F2F2), surface: Color(hex: 0xEAE9E9), text: Color(hex: 0x201E1D),
        accent: Color(hex: 0xEC3013), accent600: Color(hex: 0xDD2B0F), accent700: Color(hex: 0xAE1800),
        accent100: Color(hex: 0xFFF2EF), accent800: Color(hex: 0x7C1405),
        neutral100: Color(hex: 0xF8F4F4), neutral300: Color(hex: 0xD7D3D3), neutral500: Color(hex: 0x9B9797), neutral800: Color(hex: 0x444141),
        divider: Color(hex: 0x201E1D).opacity(0.40)
    )

    static let dark = SnapColors(
        bg: Color(hex: 0x191817), surface: Color(hex: 0x262423), text: Color(hex: 0xF3F2F2),
        accent: Color(hex: 0xFF563C), accent600: Color(hex: 0xFF9783), accent700: Color(hex: 0xFF563C),
        accent100: Color(hex: 0x4D170E), accent800: Color(hex: 0xFFC4B8),
        neutral100: Color(hex: 0x2F2C2B), neutral300: Color(hex: 0x3A3736), neutral500: Color(hex: 0x8A8686), neutral800: Color(hex: 0xD7D3D3),
        divider: Color(hex: 0xF3F2F2).opacity(0.34)
    )

    static func forScheme(_ scheme: ColorScheme) -> SnapColors { scheme == .dark ? dark : light }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

private struct SnapColorsKey: EnvironmentKey {
    static let defaultValue = SnapColors.light
}

extension EnvironmentValues {
    var snap: SnapColors {
        get { self[SnapColorsKey.self] }
        set { self[SnapColorsKey.self] = newValue }
    }
}

/// Injects the palette matching the current colour scheme.
struct SnapThemed: ViewModifier {
    @Environment(\.colorScheme) private var scheme
    func body(content: Content) -> some View {
        content.environment(\.snap, SnapColors.forScheme(scheme))
    }
}

extension View {
    func snapThemed() -> some View { modifier(SnapThemed()) }
}

/// Design rules: zero radius everywhere, 2pt rules.
enum SnapMetrics {
    static let rule: CGFloat = 2
    static let gutter: CGFloat = 20
    static let controlHeight: CGFloat = 52
}
