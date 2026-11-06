package com.poddersinthechat.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFDDD6FE),
    secondary = AccentCoral,
    onSecondary = Color.White,
    tertiary = AccentAmber,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF3B0764),
    secondary = AccentCoral,
    onSecondary = Color.White,
    tertiary = AccentAmber,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1E1E24),
    surface = Color.White,
    onSurface = Color(0xFF1E1E24),
    surfaceVariant = Color(0xFFF1F1F5),
    onSurfaceVariant = Color(0xFF6B7280)
)

@Composable
fun PoddersTheme(
    darkTheme: Boolean = true, // Default to sleek dark podcast aesthetic
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
