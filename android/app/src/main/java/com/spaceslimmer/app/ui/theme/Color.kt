package com.spaceslimmer.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand
val SlateBlue = Color(0xFF2B4C7E)
val SlateBlueDark = Color(0xFF7A9BC8)
val SavedGreen = Color(0xFF2E7D4F)
val SavedGreenDark = Color(0xFF5CB87A)

// Light canvas
val CanvasLight = Color(0xFFF2F2F7)
val CardLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFE8E8ED)
val OnSurfaceVariantLight = Color(0xFF6B7280)
val OutlineLight = Color(0xFFD1D5DB)

// Dark canvas
val CanvasDark = Color(0xFF0B0D12)
val CardDark = Color(0xFF1A1D26)
val SurfaceVariantDark = Color(0xFF2A2D36)
val OnSurfaceVariantDark = Color(0xFF9CA3AF)
val OutlineDark = Color(0xFF3F4450)

val ErrorRed = Color(0xFFDC2626)
val ErrorRedDark = Color(0xFFF87171)

val LightColorScheme = lightColorScheme(
    primary = SlateBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4F5),
    onPrimaryContainer = Color(0xFF1A2F4D),
    secondary = SavedGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4EDDA),
    onSecondaryContainer = Color(0xFF1B4332),
    tertiary = SlateBlue,
    onTertiary = Color.White,
    background = CanvasLight,
    onBackground = Color(0xFF1A1A1A),
    surface = CardLight,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = CardLight,
    surfaceContainerHighest = SurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFE5E7EB),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
)

val DarkColorScheme = darkColorScheme(
    primary = SlateBlueDark,
    onPrimary = Color(0xFF0B0D12),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD6E4F5),
    secondary = SavedGreenDark,
    onSecondary = Color(0xFF0B0D12),
    secondaryContainer = Color(0xFF1B4332),
    onSecondaryContainer = Color(0xFFD4EDDA),
    tertiary = SlateBlueDark,
    onTertiary = Color(0xFF0B0D12),
    background = CanvasDark,
    onBackground = Color(0xFFF3F4F6),
    surface = CardDark,
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = Color(0xFF22252E),
    surfaceContainerHighest = SurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF2A2D36),
    error = ErrorRedDark,
    onError = Color(0xFF0B0D12),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
)
