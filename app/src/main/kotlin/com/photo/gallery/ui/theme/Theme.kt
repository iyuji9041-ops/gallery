package com.photo.gallery.ui.theme

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
    primary = BentoAccent,
    onPrimary = BentoAccentContainer,
    primaryContainer = BentoAccentContainer,
    onPrimaryContainer = BentoAccent,
    secondary = BentoAccent,
    onSecondary = BentoAccentContainer,
    secondaryContainer = BentoSurfaceCard,
    onSecondaryContainer = BentoAccent,
    tertiary = BentoAmber,
    background = BentoBg,
    onBackground = BentoTextPrimary,
    surface = BentoSurface,
    onSurface = BentoTextPrimary,
    surfaceVariant = BentoSurfaceCard,
    onSurfaceVariant = BentoTextSecondary,
    surfaceContainer = BentoSurfaceElevated,
    surfaceContainerHigh = BentoSurfaceHighlight,
    outline = BentoBorder,
    error = BentoRose
)

private val LightColorScheme = lightColorScheme(
    primary = BentoAccent,
    onPrimary = BentoAccentContainer,
    primaryContainer = LightSurfaceElevated,
    onPrimaryContainer = BentoAccentContainer,
    secondary = BentoAccent,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightBorder,
    error = BentoRose
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}


