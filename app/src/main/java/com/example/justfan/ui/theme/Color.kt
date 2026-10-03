package com.example.justfan.ui.theme

import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF0A0D14)
val DarkSurface = Color(0xFF121622)
val DarkSurfaceVariant = Color(0xFF1B2232)
val DarkBorder = Color(0xFF263045)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

val ThemeCyan = Color(0xFF00E5FF)
val ThemeOrange = Color(0xFFFF8800)
val ThemeBlue = Color(0xFF3B82F6)
val ThemeGolden = Color(0xFFF59E0B)
val ThemeRose = Color(0xFFF43F5E)
val ThemeViolet = Color(0xFF8B5CF6)
val ThemeEmerald = Color(0xFF10B981)

val GoldAccent = Color(0xFFFFD700)
val SuccessGreen = Color(0xFF10B981)
val DangerRed = Color(0xFFEF4444)

data class ThemeVariantOption(
    val id: String,
    val label: String,
    val color: Color
)

val AVAILABLE_THEMES = listOf(
    ThemeVariantOption("cyan", "Black + Cyan", ThemeCyan),
    ThemeVariantOption("orange", "Black + Orange", ThemeOrange),
    ThemeVariantOption("blue", "Black + Blue", ThemeBlue),
    ThemeVariantOption("golden", "Black + Golden", ThemeGolden),
    ThemeVariantOption("rose", "Black + Rose", ThemeRose),
    ThemeVariantOption("violet", "Black + Violet", ThemeViolet),
    ThemeVariantOption("emerald", "Black + Emerald", ThemeEmerald)
)
