package com.example.justfan.ui.theme

import androidx.compose.ui.graphics.Color

val ThemeCyan = Color(0xFF00E5FF)
val ThemeCrimson = Color(0xFFFF1744)
val ThemeEmerald = Color(0xFF00E676)
val ThemePurple = Color(0xFFD500F9)
val ThemeSunset = Color(0xFFFF9100)
val ThemeGold = Color(0xFFFFD700)
val ThemeNeon = Color(0xFF39FF14)

val GoldAccent = Color(0xFFFFD700)
val DangerRed = Color(0xFFFF3366)
val SuccessGreen = Color(0xFF00E676)

data class ThemeOption(
    val id: String,
    val name: String,
    val color: Color
)

val AVAILABLE_THEMES = listOf(
    ThemeOption("cyan", "Cyan Pulse", ThemeCyan),
    ThemeOption("crimson", "Crimson Flame", ThemeCrimson),
    ThemeOption("emerald", "Emerald Glow", ThemeEmerald),
    ThemeOption("purple", "Ultra Violet", ThemePurple),
    ThemeOption("sunset", "Sunset Blaze", ThemeSunset),
    ThemeOption("gold", "Royal Gold", ThemeGold),
    ThemeOption("neon", "Cyberpunk Neon", ThemeNeon)
)
