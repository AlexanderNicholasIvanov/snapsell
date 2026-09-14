package com.alexivanov.snapsell.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B4D3E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFE9D6),
    secondary = Color(0xFF8A6D1F),
    secondaryContainer = Color(0xFFFFE8A3),
    tertiary = Color(0xFF4E5C8A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD8B8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF1B4D3E),
    secondary = Color(0xFFF4C95D),
    secondaryContainer = Color(0xFF5C4A00),
    tertiary = Color(0xFFB8C4F5),
)

@Composable
fun SnapSellTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
