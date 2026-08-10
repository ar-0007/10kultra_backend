package com.tenkultra.tv.presentation.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Rich, layered background with depth for the browse screens (premium feel). */
fun screenBackgroundBrush(palette: AppPalette): Brush =
    Brush.verticalGradient(
        listOf(
            lerp(palette.background, palette.surface, 0.55f),  // softly lit top
            palette.background,
            lerp(palette.background, Color.Black, 0.4f)        // deep grounded bottom
        )
    )

/**
 * Glossy button brush for category / list rows.
 * Selected → cream; unselected → a caramel-to-coffee gradient.
 */
fun classicRowBrush(palette: AppPalette, selected: Boolean): Brush =
    if (selected) {
        Brush.verticalGradient(
            listOf(lerp(palette.text, Color.White, 0.35f), palette.text)
        )
    } else {
        Brush.verticalGradient(
            listOf(
                lerp(palette.selected, palette.primary, 0.45f),
                lerp(palette.selected, palette.background, 0.30f)
            )
        )
    }
