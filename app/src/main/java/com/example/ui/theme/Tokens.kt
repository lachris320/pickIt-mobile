package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ThemeMode { DARK, LIGHT, SYSTEM }

@Immutable
data class PickItTokens(
    val canvas: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceInset: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textOnAccent: Color,
    val textAccent: Color,
    val textDanger: Color,
    val accent: Color,
    val onAccent: Color,
    val statusOpen: Color,
    val statusLive: Color,
    val statusPaused: Color,
    val attention: Color,
    val teamA: Color,
    val teamB: Color,
    val onTeamA: Color,
    val onTeamB: Color,
    val radiusSm: Dp = 8.dp,
    val radiusMd: Dp = 12.dp,
    val radiusLg: Dp = 16.dp,
)

val DarkTokens = PickItTokens(
    canvas = Color(0xFF0C110E),
    surface = Color(0xFF161F19),
    surfaceElevated = Color(0xFF1B241F),
    surfaceInset = Color(0xFF0F1713),
    border = Color(0xFF2E3D35),
    borderSubtle = Color(0xFF223029),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB7C4B5),
    textMuted = Color(0xFF7E8C7C),
    textOnAccent = Color(0xFF1B3700),
    textAccent = Color(0xFFC7E04A),
    textDanger = Color(0xFFEF5350),
    accent = Color(0xFFD4E157),
    onAccent = Color(0xFF1B3700),
    statusOpen = Color(0xFF4CAF50),
    statusLive = Color(0xFF29B6F6),
    statusPaused = Color(0xFF78909C),
    attention = Color(0xFFFFB300),
    teamA = Color(0xFF4FC3F7),
    teamB = Color(0xFFFF8A65),
    onTeamA = Color(0xFF00232B),
    onTeamB = Color(0xFF3A0E00),
)

val LightTokens = PickItTokens(
    canvas = Color(0xFFF3F5EF),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceInset = Color(0xFFECF0E6),
    border = Color(0xFFD3DBCE),
    borderSubtle = Color(0xFFE4E9DE),
    textPrimary = Color(0xFF14201A),
    textSecondary = Color(0xFF465049),
    textMuted = Color(0xFF6E7A6F),
    textOnAccent = Color(0xFF1B3700),
    textAccent = Color(0xFF3B6D11),
    textDanger = Color(0xFFC0362F),
    accent = Color(0xFFC6DB3A),
    onAccent = Color(0xFF1B3700),
    statusOpen = Color(0xFF2E7D32),
    statusLive = Color(0xFF0277BD),
    statusPaused = Color(0xFF607D8B),
    attention = Color(0xFFB26A00),
    teamA = Color(0xFF0277BD),
    teamB = Color(0xFFD84315),
    onTeamA = Color(0xFFFFFFFF),
    onTeamB = Color(0xFFFFFFFF),
)

val LocalPickItTokens = staticCompositionLocalOf { DarkTokens }
