package com.example.justfan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun JustFanTheme(
    themeVariant: String = "cyan",
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val accentColor = AVAILABLE_THEMES.find { it.id == themeVariant }?.color ?: ThemeCyan

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accentColor,
            onPrimary = Color.Black,
            primaryContainer = accentColor.copy(alpha = 0.2f),
            onPrimaryContainer = Color.White,
            secondary = ThemeViolet,
            onSecondary = Color.White,
            secondaryContainer = ThemeViolet.copy(alpha = 0.2f),
            background = DarkBackground,
            onBackground = TextPrimary,
            surface = DarkSurface,
            onSurface = TextPrimary,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = TextSecondary,
            outline = DarkBorder
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            primaryContainer = accentColor.copy(alpha = 0.15f),
            onPrimaryContainer = Color.Black,
            secondary = ThemeViolet,
            onSecondary = Color.White,
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0F172A),
            surface = Color.White,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFE2E8F0)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
