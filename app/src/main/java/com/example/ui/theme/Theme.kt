package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DarkTokens.accent, onPrimary = DarkTokens.onAccent,
    secondary = Color(0xFF00897B), onSecondary = Color.White,
    background = DarkTokens.canvas, onBackground = DarkTokens.textPrimary,
    surface = DarkTokens.surfaceElevated, onSurface = DarkTokens.textPrimary,
    outline = DarkTokens.border,
)

private val LightColorScheme = lightColorScheme(
    primary = LightTokens.accent, onPrimary = LightTokens.onAccent,
    secondary = Color(0xFF00897B), onSecondary = Color.White,
    background = LightTokens.canvas, onBackground = LightTokens.textPrimary,
    surface = LightTokens.surface, onSurface = LightTokens.textPrimary,
    outline = LightTokens.border,
)

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val tokens = if (dark) DarkTokens else LightTokens
    CompositionLocalProvider(LocalPickItTokens provides tokens) {
        MaterialTheme(
            colorScheme = if (dark) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
