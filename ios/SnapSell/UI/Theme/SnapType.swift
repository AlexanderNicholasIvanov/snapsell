import SwiftUI

/// Archivo type roles from the design hand-off. Sizes are points; tracking in em.
struct SnapTextStyle {
    enum Weight { case regular, semibold, extrabold
        var fontName: String {
            switch self {
            case .regular: return "Archivo-Regular"
            case .semibold: return "Archivo-SemiBold"
            case .extrabold: return "Archivo-ExtraBold"
            }
        }
    }

    var size: CGFloat
    var weight: Weight
    var tracking: CGFloat = 0   // em
    var lineHeight: CGFloat? = nil

    var font: Font { .custom(weight.fontName, size: size) }
    var kerning: CGFloat { tracking * size }
}

enum SnapType {
    static let displayPrice = SnapTextStyle(size: 46, weight: .extrabold, tracking: -0.03)
    static let wordmark = SnapTextStyle(size: 46, weight: .extrabold, tracking: -0.04)
    static let bundlePrice = SnapTextStyle(size: 34, weight: .extrabold, tracking: -0.02)
    static let screenHero = SnapTextStyle(size: 30, weight: .extrabold, tracking: -0.02)
    static let soldRange = SnapTextStyle(size: 30, weight: .extrabold, tracking: -0.02)
    static let cardPrice = SnapTextStyle(size: 28, weight: .extrabold, tracking: -0.02)
    static let sectionHead = SnapTextStyle(size: 21, weight: .extrabold, tracking: -0.01)
    static let statValue = SnapTextStyle(size: 20, weight: .extrabold, tracking: -0.01)
    static let rowPrice = SnapTextStyle(size: 19, weight: .extrabold, tracking: -0.01)
    static let fieldValueBold = SnapTextStyle(size: 19, weight: .extrabold, tracking: -0.01)
    static let appBarTitle = SnapTextStyle(size: 18, weight: .extrabold, tracking: -0.01)
    static let rowTitle = SnapTextStyle(size: 15, weight: .semibold)
    static let buttonLabel = SnapTextStyle(size: 15, weight: .semibold, tracking: 0.01)
    static let bodyLarge = SnapTextStyle(size: 15, weight: .regular)
    static let fieldValue = SnapTextStyle(size: 15, weight: .regular)
    static let tabLabel = SnapTextStyle(size: 14, weight: .semibold)
    static let body = SnapTextStyle(size: 14, weight: .regular)
    static let bodySmall = SnapTextStyle(size: 13, weight: .regular)
    static let chipLabel = SnapTextStyle(size: 12.5, weight: .semibold)
    static let fieldLabel = SnapTextStyle(size: 12, weight: .semibold, tracking: 0.04)
    static let tag = SnapTextStyle(size: 10.5, weight: .semibold, tracking: 0.08)
    static let microLabel = SnapTextStyle(size: 10, weight: .semibold, tracking: 0.10)
    static let statLabel = SnapTextStyle(size: 9.5, weight: .semibold, tracking: 0.08)
}

extension View {
    func snapText(_ style: SnapTextStyle) -> some View {
        font(style.font).kerning(style.kerning)
    }
}

extension Text {
    func snap(_ style: SnapTextStyle) -> Text {
        font(style.font).kerning(style.kerning)
    }
}
