package com.tenkultra.tv.presentation.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tenkultra.tv.presentation.theme.appPalette

/** Three gently pulsing dots — a lightweight, premium loading indicator. */
@Composable
fun LoadingDots(modifier: Modifier = Modifier) {
    val palette = appPalette
    val transition = rememberInfiniteTransition(label = "dots")
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val a by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.3f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0.3f at 0
                        1f at 300
                        0.3f at 600
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(i * 150)
                ),
                label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .alpha(a)
                    .clip(CircleShape)
                    .background(palette.primary)
            )
            if (i < 2) Spacer(Modifier.width(7.dp))
        }
    }
}
