package com.tenkultra.tv.presentation.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Premium focus treatment: a springy (bouncy) pop + a coloured glow shadow that lifts the
 * focused item off the surface. Chain it BEFORE `.clip()/.background()`. Used across both
 * layouts so selection feels alive and elevated.
 */
@Composable
fun Modifier.premiumFocus(
    focused: Boolean,
    shape: Shape,
    glow: Color,
    scaleTo: Float = 1.07f,
    lift: Int = 18
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (focused) scaleTo else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "pfScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (focused) lift.dp else 0.dp,
        animationSpec = tween(200),
        label = "pfElev"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .shadow(elevation, shape, ambientColor = glow, spotColor = glow)
}
