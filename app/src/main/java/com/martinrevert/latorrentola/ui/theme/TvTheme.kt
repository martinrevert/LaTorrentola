package com.martinrevert.latorrentola.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme

/**
 * Extension to map mobile Material 3 ColorScheme to TV Material 3 ColorScheme.
 */
fun androidx.compose.material3.ColorScheme.toTvColorScheme(): ColorScheme {
    return ColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        inversePrimary = inversePrimary,
        surfaceTint = surfaceTint,
        scrim = scrim,
        border = outline,
        borderVariant = outlineVariant
    )
}

@Composable
fun TvLaTorrentolaTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val mobileColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val tvColorScheme = mobileColorScheme.toTvColorScheme()

    MaterialTheme(
        colorScheme = tvColorScheme
    ) {
        // Provide the mobile MaterialTheme as well so mobile M3 components (like Scaffold, Text)
        // work correctly and pick up the same colors.
        androidx.compose.material3.MaterialTheme(
            colorScheme = mobileColorScheme,
            typography = com.martinrevert.latorrentola.ui.theme.Typography,
            content = content
        )
    }
}
