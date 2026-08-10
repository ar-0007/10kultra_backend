package com.tenkultra.tv.presentation.screens.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenkultra.tv.presentation.common.BrandWordmark
import com.tenkultra.tv.presentation.theme.appPalette

@Composable
fun SplashScreen(
    onNavigateToLocked: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToBlocked: (String?) -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val palette = appPalette
    val destination by viewModel.destination.collectAsStateWithLifecycle()

    LaunchedEffect(destination) {
        when (destination) {
            SplashDestination.LOCKED -> onNavigateToLocked()
            SplashDestination.HOME -> onNavigateToHome()
            SplashDestination.BLOCKED -> onNavigateToBlocked(viewModel.blockMessage)
            null -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedLogo()
            IndeterminateProgressBar(
                modifier = Modifier
                    .padding(top = 44.dp)
                    .width(240.dp)
                    .height(4.dp)
            )
        }
    }
}

@Composable
private fun AnimatedLogo() {
    // Entrance: fade + scale-in once.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val enterScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.82f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "enterScale"
    )
    val enterAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "enterAlpha"
    )

    // Continuous: gentle breathing pulse.
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    BrandWordmark(
        modifier = Modifier
            .width(520.dp)
            .alpha(enterAlpha)
            .scale(enterScale * pulse)
    )
}

@Composable
private fun IndeterminateProgressBar(modifier: Modifier = Modifier) {
    val palette = appPalette
    val transition = rememberInfiniteTransition(label = "progress")
    val progress by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Restart
        ),
        label = "progressOffset"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(palette.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(4.dp)
                .graphicsLayer { translationX = progress * size.width / 0.4f }
                .clip(RoundedCornerShape(2.dp))
                .background(palette.primary)
        )
    }
}
