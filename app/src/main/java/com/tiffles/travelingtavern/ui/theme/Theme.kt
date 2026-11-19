package com.tiffles.travelingtavern.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Secondary maps to the same amber so NavigationBar's selected-tab indicator
// (secondaryContainer) uses the accent. Tertiary stays neutral.
private val DarkColorScheme = darkColorScheme(
    primary = AmberDark,
    onPrimary = OnAmberDark,
    primaryContainer = AmberContainerDark,
    onPrimaryContainer = OnAmberContainerDark,
    secondary = AmberDark,
    onSecondary = OnAmberDark,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = OnAmberContainerDark,
    tertiary = OnSurfaceVariantDark,
    onTertiary = SurfaceDark,
    tertiaryContainer = SurfaceContainerHighDark,
    onTertiaryContainer = OnSurfaceDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    // Snackbars use the inverse roles: the light theme's values, so their action stays amber.
    inverseSurface = SurfaceContainerHighLight,
    inverseOnSurface = OnSurfaceLight,
    inversePrimary = AmberLight
)

private val LightColorScheme = lightColorScheme(
    primary = AmberLight,
    onPrimary = OnAmberLight,
    primaryContainer = AmberContainerLight,
    onPrimaryContainer = OnAmberContainerLight,
    secondary = AmberLight,
    onSecondary = OnAmberLight,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = OnAmberContainerLight,
    tertiary = OnSurfaceVariantLight,
    onTertiary = SurfaceLight,
    tertiaryContainer = SurfaceContainerHighLight,
    onTertiaryContainer = OnSurfaceLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    // Snackbars use the inverse roles: the dark theme's values, so their action stays amber.
    inverseSurface = SurfaceContainerHighDark,
    inverseOnSurface = OnSurfaceDark,
    inversePrimary = AmberDark
)

/**
 * App-wide Material 3 theme: neutral surfaces with one amber accent.
 *
 * Dynamic color (Material You) is off by default. [useDynamicColor] is hard-coded
 * `false` by the caller until 1.8 adds the "Use wallpaper colors" toggle in Me → Appearance.
 */
@Composable
fun TravelingTavernTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        useDynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}