package com.alexivanov.snapsell.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Design tokens from docs/design-handoff.md. Everything the handoff names is
 * here; the dark values it does not name (accent-600/700, the neutrals) are
 * derived and marked as such.
 */
@Immutable
data class SnapColors(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val accent: Color,
    val accent600: Color,
    val accent700: Color,
    val accent100: Color,
    val accent800: Color,
    val neutral100: Color,
    val neutral300: Color,
    val neutral500: Color,
    val neutral800: Color,
    /** "text @ 40%" light / "text @ 34%" dark. Every rule and border. */
    val divider: Color,
    val isDark: Boolean,
) {
    /** Cutouts always sit on pure white, in both themes. */
    val cutoutWhite: Color get() = Color.White
    val shadow: Color get() = if (isDark) Color(0xA6000000) else Color(0x382D2B2B)
    val scrim: Color get() = Color(0x99141211)
}

val SnapLightColors = SnapColors(
    bg = Color(0xFFF3F2F2),
    surface = Color(0xFFEAE9E9),
    text = Color(0xFF201E1D),
    accent = Color(0xFFEC3013),
    accent600 = Color(0xFFDD2B0F),
    accent700 = Color(0xFFAE1800),
    accent100 = Color(0xFFFFF2EF),
    accent800 = Color(0xFF7C1405),
    neutral100 = Color(0xFFF8F4F4),
    neutral300 = Color(0xFFD7D3D3),
    neutral500 = Color(0xFF9B9797),
    neutral800 = Color(0xFF444141),
    divider = Color(0xFF201E1D).copy(alpha = 0.40f),
    isDark = false,
)

val SnapDarkColors = SnapColors(
    bg = Color(0xFF191817),
    surface = Color(0xFF262423),
    text = Color(0xFFF3F2F2),
    accent = Color(0xFFFF563C),
    // Derived: the handoff gives accent-400 (#ff9783) as the pressed state on dark.
    accent600 = Color(0xFFFF9783),
    // Derived: accent-coloured text on a dark ground uses the lifted accent itself;
    // the light accent-700 (#ae1800) would be illegible here.
    accent700 = Color(0xFFFF563C),
    accent100 = Color(0xFF4D170E),
    accent800 = Color(0xFFFFC4B8),
    // Derived neutrals for dark (not in the handoff): a step above surface for
    // tag fills, and light greys for tag text / placeholder blocks / ticks.
    neutral100 = Color(0xFF2F2C2B),
    neutral300 = Color(0xFF3A3736),
    neutral500 = Color(0xFF8A8686),
    neutral800 = Color(0xFFD7D3D3),
    divider = Color(0xFFF3F2F2).copy(alpha = 0.34f),
    isDark = true,
)

val LocalSnapColors = staticCompositionLocalOf { SnapLightColors }
