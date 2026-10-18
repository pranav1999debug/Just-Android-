package com.example.justfan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun JustFanTheme(
    themeVariant: String = "cyan",
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val targetPrimary = AVAILABLE_THEMES.find { it.id.equals(themeVariant, ignoreCase = true) }?.color ?: ThemeCyan

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = targetPrimary,
            onPrimary = Color.Black,
            primaryContainer = targetPrimary.copy(alpha = 0.2f),
            onPrimaryContainer = targetPrimary,
            secondary = GoldAccent,
            onSecondary = Color.Black,
            background = Color(0xFF0F1015),
            onBackground = Color(0xFFE2E2E8),
            surface = Color(0xFF161821),
            onSurface = Color(0xFFE2E2E8),
            surfaceVariant = Color(0xFF202330),
            onSurfaceVariant = Color(0xFFA0A3B5)
        )
    } else {
        lightColorScheme(
            primary = targetPrimary,
            onPrimary = Color.White,
            primaryContainer = targetPrimary.copy(alpha = 0.15f),
            onPrimaryContainer = Color(0xFF00363D),
            secondary = GoldAccent,
            onSecondary = Color.Black,
            background = Color(0xFFF7F8FA),
            onBackground = Color(0xFF191C20),
            surface = Color.White,
            onSurface = Color(0xFF191C20),
            surfaceVariant = Color(0xFFE8EAF0),
            onSurfaceVariant = Color(0xFF434752)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
