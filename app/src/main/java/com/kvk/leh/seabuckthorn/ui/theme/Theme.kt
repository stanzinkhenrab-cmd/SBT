package com.kvk.leh.seabuckthorn.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = BerryOrange40,
    onPrimary = Color.White,
    primaryContainer = BerryOrangeContainerLight,
    onPrimaryContainer = BerryOrange40,
    secondary = LeafGreen40,
    onSecondary = Color.White,
    secondaryContainer = LeafGreenContainerLight,
    onSecondaryContainer = LeafGreen40,
    tertiary = SkyBlue40,
    background = MountainGreyLight,
    surface = SurfaceLight,
    error = ErrorRed40
)

private val DarkColors = darkColorScheme(
    primary = BerryOrange80,
    onPrimary = BerryOrangeContainerDark,
    primaryContainer = BerryOrangeContainerDark,
    onPrimaryContainer = BerryOrange80,
    secondary = LeafGreen80,
    onSecondary = LeafGreenContainerDark,
    secondaryContainer = LeafGreenContainerDark,
    onSecondaryContainer = LeafGreen80,
    tertiary = SkyBlue80,
    background = MountainGreyDark,
    surface = SurfaceDark,
    error = ErrorRed80
)

/**
 * The app deliberately does NOT opt into Android 12+ dynamic (wallpaper-derived) color.
 * A consistent seabuckthorn-inspired palette is part of the scientific-app brand identity,
 * so it stays fixed across devices rather than shifting with the user's wallpaper.
 */
@Composable
fun SeabuckthornTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SeabuckthornTypography,
        content = content
    )
}
