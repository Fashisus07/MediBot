package com.medibot.g1.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MediBotDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = DarkBackground,
    primaryContainer = NeonCyan.copy(alpha = 0.15f),
    onPrimaryContainer = NeonCyan,
    secondary = ElectricViolet,
    onSecondary = DarkBackground,
    secondaryContainer = ElectricViolet.copy(alpha = 0.15f),
    onSecondaryContainer = ElectricViolet,
    tertiary = TerminalGreen,
    onTertiary = DarkBackground,
    error = CriticalRed,
    onError = DarkBackground,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurface,
    outline = CardNormalBorder
)

@Composable
fun MediBotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MediBotDarkColorScheme,
        typography = MediBotTypography,
        content = content
    )
}
