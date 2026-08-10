package com.tenkultra.tv.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.tenkultra.tv.domain.model.AppTheme

/** Provides the active [AppPalette] to descendant composables. */
val LocalAppPalette = staticCompositionLocalOf { GoldPalette }

/** Convenience accessor: `AppTheme.palette` inside composables. */
val appPalette: AppPalette
    @Composable get() = LocalAppPalette.current

@Composable
fun TenKUltraTheme(
    theme: AppTheme,
    content: @Composable () -> Unit
) {
    val palette = when (theme) {
        AppTheme.BRAND -> GoldPalette
        AppTheme.CLASSIC -> ClassicPalette
        AppTheme.MODERN -> ModernPalette
    }

    val colorScheme = darkColorScheme(
        primary = palette.primary,
        onPrimary = Color.White,
        secondary = palette.accent,
        onSecondary = Color.White,
        background = palette.background,
        onBackground = palette.text,
        surface = palette.surface,
        onSurface = palette.text,
        surfaceVariant = palette.surface,
        onSurfaceVariant = palette.textSecondary
    )

    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
