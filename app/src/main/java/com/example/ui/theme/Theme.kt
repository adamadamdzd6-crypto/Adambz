package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color(0xFF021B24),
    primaryContainer = Color(0xFF083344),
    onPrimaryContainer = AccentCyanLight,
    secondary = AccentPurple,
    onSecondary = Color(0xFF1E1035),
    secondaryContainer = Color(0xFF2E1065),
    onSecondaryContainer = Color(0xFFDDD6FE),
    tertiary = AccentEmerald,
    onTertiary = Color(0xFF022C22),
    background = StudioDarkBg,
    onBackground = Color(0xFFF1F5F9),
    surface = StudioDarkSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = StudioDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = StudioDarkSurfaceBorder,
    error = AccentRose,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF5B21B6),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = StudioLightBg,
    onBackground = Color(0xFF0F172A),
    surface = StudioLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = StudioLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = StudioLightSurfaceBorder,
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun CinePromptTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CineTypography,
        content = content
    )
}
