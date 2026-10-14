package com.spaceslimmer.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalSavingsColor = staticCompositionLocalOf { Color.Unspecified }

@Composable
fun savingsColor(): Color {
    val local = LocalSavingsColor.current
    return if (local == Color.Unspecified) MaterialTheme.colorScheme.secondary else local
}

@Composable
fun SpaceSlimmerTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(LocalSavingsColor provides colorScheme.secondary) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SpaceSlimmerTypography,
            shapes = SpaceSlimmerShapes,
            content = content,
        )
    }
}
