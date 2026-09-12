package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PickleballLime,
    onPrimary = Color(0xFF1B3700),
    primaryContainer = PickleballLimeContainer,
    onPrimaryContainer = Color(0xFFE4F87E),
    secondary = PickleballEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF003831),
    onSecondaryContainer = Color(0xFF73F8DE),
    tertiary = TeamBColor,
    background = CanvasDark,
    onBackground = WhiteHighContrast,
    surface = PickleballCardSurface,
    onSurface = WhiteHighContrast,
    surfaceVariant = Color(0xFF232F28),
    onSurfaceVariant = Color(0xFFCFD8DC),
    outline = PickleballCardBorder
)

private val LightColorScheme = darkColorScheme( // We prefer a dark high-contrast court theme even in daylight for glare reduction
    primary = PickleballLime,
    onPrimary = Color(0xFF1B3700),
    secondary = PickleballEmerald,
    background = CanvasDark,
    surface = PickleballCardSurface,
    onBackground = WhiteHighContrast,
    onSurface = WhiteHighContrast
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our handcrafted court theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
