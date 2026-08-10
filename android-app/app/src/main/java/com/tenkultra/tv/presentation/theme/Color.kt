package com.tenkultra.tv.presentation.theme

import androidx.compose.ui.graphics.Color

/** Palette shared across screens, switched by the selected [com.tenkultra.tv.domain.model.AppTheme]. */
data class AppPalette(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val selected: Color,
    val text: Color,
    val textSecondary: Color,
    val liveDot: Color,
    val accent: Color
)

/** Brand — 10K Ultra onyx + liquid gold, sampled directly from the logo. */
val GoldPalette = AppPalette(
    background = Color(0xFF07070A), // onyx — the logo's black field
    surface = Color(0xFF15130E),    // warm charcoal card (a hint of gold in the black)
    primary = Color(0xFFE7B53C),    // bright gold highlight (logo bevel)
    selected = Color(0xFFA9781B),   // deep antique gold (logo shadow side)
    text = Color(0xFFF6EEDD),       // warm ivory
    textSecondary = Color(0xFFA69B85), // muted sand
    liveDot = Color(0xFF4CC97A),
    accent = Color(0xFFFFD766)      // pale gold glow
)

/** Classic — STBEMU-style, lighter sky-blue accents on a deep navy base. */
val ClassicPalette = AppPalette(
    background = Color(0xFF0E1524), // deep navy, a touch lighter/bluer than before
    surface = Color(0xFF17223A),   // lifted blue-grey card
    primary = Color(0xFF5FC4FF),   // light sky blue (was dark 1E90FF)
    selected = Color(0xFF3A9BE8),  // medium blue for the selected fill
    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB8C4D6), // cooler light-grey
    liveDot = Color(0xFF22C55E),
    accent = Color(0xFF5FC4FF)
)

/** Modern — Netflix dark style, with its signature red accent. */
val ModernPalette = AppPalette(
    background = Color(0xFF141414),
    surface = Color(0xFF1F1F1F),
    primary = Color(0xFFE50914),  // Netflix red
    selected = Color(0xFFB81D24),
    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF999999),
    liveDot = Color(0xFF46D369),
    accent = Color(0xFFE50914)
)
