package dev.eduarddragu.anotherhabittracker.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors =
  lightColorScheme(
    primary = Light.accent,
    onPrimary = Light.background,
    primaryContainer = Light.accentContainer,
    onPrimaryContainer = Light.text,
    secondary = Light.muted,
    onSecondary = Light.background,
    // Selected chips and similar use the secondary container: without these it is Material's lilac.
    secondaryContainer = Light.accentContainer,
    onSecondaryContainer = Light.text,
    tertiaryContainer = Light.accentContainer,
    onTertiaryContainer = Light.text,
    background = Light.background,
    onBackground = Light.text,
    surface = Light.background,
    onSurface = Light.text,
    surfaceVariant = Light.subtle,
    onSurfaceVariant = Light.muted,
    surfaceContainerLowest = Light.background,
    surfaceContainerLow = Light.subtle,
    surfaceContainer = Light.subtle,
    surfaceContainerHigh = Light.subtle,
    surfaceContainerHighest = Light.border,
    outline = Light.borderStrong,
    outlineVariant = Light.border,
    // Without these the snackbar and a few components fall back to Material's baseline purple-grey.
    tertiary = Light.muted,
    onTertiary = Light.background,
    inverseSurface = Light.text,
    inverseOnSurface = Light.background,
    inversePrimary = Dark.accent,
    surfaceTint = Color.Transparent,
    // Errors speak in the accent, like the rest of the app, not in Material red.
    error = Light.accent,
  )

private val DarkColors =
  darkColorScheme(
    primary = Dark.accent,
    onPrimary = Dark.background,
    primaryContainer = Dark.accentContainer,
    onPrimaryContainer = Dark.text,
    secondary = Dark.muted,
    onSecondary = Dark.background,
    secondaryContainer = Dark.accentContainer,
    onSecondaryContainer = Dark.text,
    tertiaryContainer = Dark.accentContainer,
    onTertiaryContainer = Dark.text,
    background = Dark.background,
    onBackground = Dark.text,
    surface = Dark.background,
    onSurface = Dark.text,
    surfaceVariant = Dark.subtle,
    onSurfaceVariant = Dark.muted,
    surfaceContainerLowest = Dark.background,
    surfaceContainerLow = Dark.subtle,
    surfaceContainer = Dark.subtle,
    surfaceContainerHigh = Dark.subtle,
    surfaceContainerHighest = Dark.borderStrong,
    outline = Dark.borderStrong,
    // One step lighter than the plain border: on the dark background the border color nearly vanishes
    // (missed days, empty heatmap cells, hairlines).
    outlineVariant = Dark.borderStrong,
    tertiary = Dark.muted,
    onTertiary = Dark.background,
    inverseSurface = Dark.text,
    inverseOnSurface = Dark.background,
    inversePrimary = Light.accent,
    surfaceTint = Color.Transparent,
    error = Dark.accent,
  )

/** Controls (score squares, icon picker) are medium; every card, on every screen, is large. */
private val AppShapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(24.dp))

/** Fixed brand palette: dynamic (wallpaper) colors are deliberately off. */
@Composable
fun AnotherHabitTrackerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography, shapes = AppShapes, content = content)
}
