package com.kvkleh.sbtsurvey.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Seabuckthorn berry orange, Ladakh sage-green foliage and glacier blue.
val BerryOrange = Color(0xFFA84B00)
val BerryOrangeLight = Color(0xFFFFDCC0)
val BerryOrangeDeep = Color(0xFF351100)
val LeafSage = Color(0xFF4C6A3E)
val LeafSageLight = Color(0xFFCEEFB8)
val LeafSageDeep = Color(0xFF0C2004)
val GlacierBlue = Color(0xFF37618E)
val GlacierBlueLight = Color(0xFFD2E4FF)
val GlacierBlueDeep = Color(0xFF001D36)
val SurfaceCream = Color(0xFFFFFBF7)
val SurfaceCreamDim = Color(0xFFF7EBE1)
val InkBrown = Color(0xFF201A15)
val StoneBrown = Color(0xFF52443A)
val OutlineBrown = Color(0xFF857468)
val OutlineBrownSoft = Color(0xFFD8C4B6)

/**
 * A single light colour scheme is used on every device. Surveys are filled in
 * outdoors in strong Ladakh sunlight, where a dark surface is much harder to
 * read, so the app deliberately does not follow the system dark setting.
 */
private val SbtLightColors = lightColorScheme(
    primary = BerryOrange,
    onPrimary = Color.White,
    primaryContainer = BerryOrangeLight,
    onPrimaryContainer = BerryOrangeDeep,
    secondary = LeafSage,
    onSecondary = Color.White,
    secondaryContainer = LeafSageLight,
    onSecondaryContainer = LeafSageDeep,
    tertiary = GlacierBlue,
    onTertiary = Color.White,
    tertiaryContainer = GlacierBlueLight,
    onTertiaryContainer = GlacierBlueDeep,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = SurfaceCream,
    onBackground = InkBrown,
    surface = SurfaceCream,
    onSurface = InkBrown,
    surfaceVariant = SurfaceCreamDim,
    onSurfaceVariant = StoneBrown,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF6EE),
    surfaceContainer = Color(0xFFFCF0E6),
    surfaceContainerHigh = Color(0xFFF7EAE0),
    surfaceContainerHighest = Color(0xFFF1E4DA),
    outline = OutlineBrown,
    outlineVariant = OutlineBrownSoft,
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF362F2A),
    inverseOnSurface = Color(0xFFFBEFE7),
    inversePrimary = Color(0xFFFFB68A)
)

@Composable
fun SbtSurveyTheme(content: @Composable () -> Unit) {
    val colorScheme = SbtLightColors
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                // The app bar behind the status bar is the deep berry orange, so
                // the system icons above it must stay light.
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = true
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SbtTypography,
        shapes = SbtShapes,
        content = content
    )
}
