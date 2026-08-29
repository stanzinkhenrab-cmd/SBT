package com.sbt.geostamp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Forest = Color(0xFF2E7D32)
private val ForestLight = Color(0xFF7FD1A8)
private val Charcoal = Color(0xFF121513)

private val DarkColors = darkColorScheme(
    primary = ForestLight,
    onPrimary = Color(0xFF06331F),
    secondary = Color(0xFFA8D5BA),
    background = Charcoal,
    onBackground = Color(0xFFECEFEC),
    surface = Color(0xFF1A1E1B),
    onSurface = Color(0xFFECEFEC),
    surfaceVariant = Color(0xFF262B27),
    onSurfaceVariant = Color(0xFFC4CBC5)
)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    secondary = Color(0xFF3F6B52),
    background = Color(0xFFF6F8F5),
    onBackground = Color(0xFF161A17),
    surface = Color.White,
    onSurface = Color(0xFF161A17),
    surfaceVariant = Color(0xFFE3E9E4),
    onSurfaceVariant = Color(0xFF414A44)
)

@Composable
fun GeoStampTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
