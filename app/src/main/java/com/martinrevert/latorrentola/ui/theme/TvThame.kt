package com.martinrevert.latorrentola.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import androidx.tv.material3.lightColorScheme as tvLightColorScheme
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.compose.material3.MaterialTheme as MobileMaterialTheme
import com.martinrevert.latorrentola.utils.PreferenceManager

private val tvLightScheme = tvLightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    border = outlineLight,
    borderVariant = outlineVariantLight
)

private val tvDarkScheme = tvDarkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    border = outlineDark,
    borderVariant = outlineVariantDark
)

/** Provides the TV Material theme and a matching mobile Material 3 theme to descendants. */
@Composable
fun TvLaTorrentolaTheme(
    themeMode: Int = PreferenceManager.THEME_SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val actualDarkTheme = when (themeMode) {
        PreferenceManager.THEME_LIGHT -> false
        PreferenceManager.THEME_DARK -> true
        else -> darkTheme
    }

    val mobileColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (actualDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        actualDarkTheme -> darkScheme
        else -> lightScheme
    }

    val tvColorScheme = if (actualDarkTheme) tvDarkScheme else tvLightScheme

    TvMaterialTheme(
        colorScheme = tvColorScheme
    ) {
        MobileMaterialTheme(
            colorScheme = mobileColorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
