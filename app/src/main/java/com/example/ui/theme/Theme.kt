package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ShopDarkPrimary,
    onPrimary = ShopDarkOnPrimary,
    primaryContainer = ShopDarkPrimaryContainer,
    onPrimaryContainer = ShopDarkOnPrimaryContainer,
    secondary = ShopDarkSecondary,
    onSecondary = ShopDarkOnSecondary,
    secondaryContainer = ShopDarkSecondaryContainer,
    onSecondaryContainer = ShopDarkOnSecondaryContainer,
    background = ShopDarkBackground,
    onBackground = ShopDarkOnBackground,
    surface = ShopDarkSurface,
    onSurface = ShopDarkOnSurface,
    surfaceVariant = ShopDarkSurfaceVariant,
    onSurfaceVariant = ShopDarkOnSurfaceVariant,
    outline = ShopDarkOutline,
    error = ShopError,
    onError = ShopOnError
)

private val LightColorScheme = lightColorScheme(
    primary = ShopPrimary,
    onPrimary = ShopOnPrimary,
    primaryContainer = ShopPrimaryContainer,
    onPrimaryContainer = ShopOnPrimaryContainer,
    secondary = ShopSecondary,
    onSecondary = ShopOnSecondary,
    secondaryContainer = ShopSecondaryContainer,
    onSecondaryContainer = ShopOnSecondaryContainer,
    tertiary = ShopTertiary,
    onTertiary = ShopOnTertiary,
    tertiaryContainer = ShopTertiaryContainer,
    onTertiaryContainer = ShopOnTertiaryContainer,
    background = ShopBackground,
    onBackground = ShopOnBackground,
    surface = ShopSurface,
    onSurface = ShopOnSurface,
    surfaceVariant = ShopSurfaceVariant,
    onSurfaceVariant = ShopOnSurfaceVariant,
    outline = ShopOutline,
    error = ShopError,
    onError = ShopOnError,
    errorContainer = ShopErrorContainer,
    onErrorContainer = ShopOnErrorContainer
)

@Composable
fun ShopTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep branded vibrant palette consistent
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
