package com.alexivanov.snapsell.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/**
 * Radius is 0 everywhere (the capture shutter is drawn as a circle by hand).
 * M3's Shapes wants CornerBasedShape, so a 0dp rounded shape stands in for
 * RectangleShape; visually identical.
 */
private val Square = RoundedCornerShape(0.dp)
val ModernistShapes = Shapes(
    extraSmall = Square,
    small = Square,
    medium = Square,
    large = Square,
    extraLarge = Square,
)

private fun SnapColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        // On dark the primary label is #191817, not white.
        onPrimary = bg,
        primaryContainer = accent100,
        onPrimaryContainer = accent800,
        secondary = text,
        onSecondary = bg,
        secondaryContainer = surface,
        onSecondaryContainer = text,
        tertiary = accent700,
        onTertiary = bg,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surface,
        onSurfaceVariant = text.copy(alpha = 0.70f),
        surfaceContainer = surface,
        surfaceContainerLow = bg,
        surfaceContainerLowest = bg,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        error = accent700,
        onError = bg,
        errorContainer = accent100,
        onErrorContainer = accent800,
        outline = divider,
        outlineVariant = divider,
        scrim = scrim,
    )
}

@Composable
fun SnapSellTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) SnapDarkColors else SnapLightColors
    CompositionLocalProvider(LocalSnapColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = SnapTypography,
            shapes = ModernistShapes,
            content = content,
        )
    }
}

/** Shorthand for the token set of the current theme. */
val snapColors: SnapColors
    @Composable get() = LocalSnapColors.current
