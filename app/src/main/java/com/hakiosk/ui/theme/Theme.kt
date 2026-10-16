package com.hakiosk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object KioskColors {
    val Positive = Color(0xFF4CD08A)
    val Negative = Color(0xFFFF6B6B)
    val Warm = Color(0xFFF6C26B)
    val Live = Color(0xFFFF4D4D)
}

private val KioskColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1A33),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFF6C26B),
    onSecondary = Color(0xFF2B1D00),
    secondaryContainer = Color(0xFF4A3A16),
    onSecondaryContainer = Color(0xFFFFDFA6),
    tertiary = Color(0xFF7FD8C6),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE3E6EB),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE3E6EB),
    surfaceVariant = Color(0xFF242A33),
    onSurfaceVariant = Color(0xFFA9B1BD),
    surfaceContainerLow = Color(0xFF12161C),
    surfaceContainer = Color(0xFF171C23),
    surfaceContainerHigh = Color(0xFF1F252E),
    surfaceContainerHighest = Color(0xFF29303A),
    outline = Color(0xFF3A424D),
    outlineVariant = Color(0xFF2A3038),
    error = Color(0xFFFF8A80),
)

@Composable
fun KioskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KioskColorScheme,
        typography = Typography(),
        content = content,
    )
}
