package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BroadcastDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF002029),
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = ElectricCyanBright,
    secondary = BroadcastIndigo,
    onSecondary = Color.White,
    secondaryContainer = BroadcastIndigoContainer,
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = SignalGreen,
    onTertiary = Color(0xFF002116),
    tertiaryContainer = SignalGreenContainer,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = ControlDeckSurface,
    onSurface = TextPrimary,
    surfaceVariant = ControlDeckSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderSlate,
    error = AlertCrimson,
    onError = Color.White,
    errorContainer = AlertCrimsonContainer,
    onErrorContainer = Color(0xFFFECACA)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BroadcastDarkColorScheme,
        typography = Typography,
        content = content
    )
}
