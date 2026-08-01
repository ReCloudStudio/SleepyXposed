package io.github.recloudstudio.sleepyxposed.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Static palette — no dynamic Monet / heavy theme controller.
private val TealActive = Color(0xFF0D5C63)
private val TealPrimary = Color(0xFF0D5C63)
private val LinkBlue = Color(0xFF0A7EA4)
private val BackgroundLight = Color(0xFFF5F6F8)
private val SurfaceLight = Color(0xFFFFFFFF)
private val OnSurface = Color(0xFF1C1B1F)
private val OnSurfaceVariant = Color(0xFF6B7280)
private val Outline = Color(0xFFE5E7EB)
private val NavSelectedContainer = Color(0xFFD6EAF0)

private val LightColors =
    lightColorScheme(
        primary = TealPrimary,
        onPrimary = Color.White,
        primaryContainer = NavSelectedContainer,
        onPrimaryContainer = TealPrimary,
        secondary = LinkBlue,
        onSecondary = Color.White,
        background = BackgroundLight,
        onBackground = OnSurface,
        surface = SurfaceLight,
        onSurface = OnSurface,
        surfaceVariant = Color(0xFFF0F1F3),
        onSurfaceVariant = OnSurfaceVariant,
        outline = Outline,
        error = Color(0xFFB3261E)
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF7EC8CF),
        onPrimary = Color(0xFF00363A),
        primaryContainer = Color(0xFF1A3A3E),
        onPrimaryContainer = Color(0xFFB5E4E8),
        secondary = Color(0xFF8EC8DC),
        onSecondary = Color(0xFF003544),
        background = Color(0xFF121416),
        onBackground = Color(0xFFE4E2E6),
        surface = Color(0xFF1C1E21),
        onSurface = Color(0xFFE4E2E6),
        surfaceVariant = Color(0xFF2A2D31),
        onSurfaceVariant = Color(0xFFC4C7CC),
        outline = Color(0xFF3D4248),
        error = Color(0xFFF2B8B5)
    )

val StatusActiveColor = TealActive
val StatusInactiveColor = Color(0xFF6B7280)

@Composable
fun SleepyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
