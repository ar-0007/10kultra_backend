package com.tenkultra.tv.presentation.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import com.tenkultra.tv.presentation.screens.player.PlayerViewModel

/**
 * MOBILE video player — native media3 [PlayerView] with the built-in TOUCH controller (tap to
 * show/hide, drag seek-bar, ±10s, play/pause). Reuses [PlayerViewModel] (same stream resolution as
 * the TV player); we only swap the D-pad overlay for finger controls.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MobilePlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    val isLive = viewModel.isLiveStream

    // Video plays in LANDSCAPE on mobile (portrait video looks tiny); restore PORTRAIT on exit so
    // the rest of the mobile app stays portrait.
    DisposableEffect(Unit) {
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val player = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Lavf/57.83.100")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            // See PlayerScreen: provider VOD origins can take ~17s to hand over one 6s segment,
            // and a 15s read timeout aborts that mid-transfer.
            .setReadTimeoutMs(40_000)
        // The phone had no LoadControl at all, so it ran on ExoPlayer's 50s cap. Buffer far ahead
        // instead, for exactly the reason described in PlayerScreen's VOD branch.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 2_500, 5_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                setWakeMode(C.WAKE_MODE_NETWORK)
                // Mobile: set video scaling to FIT so the full frame is always visible
                // without any cropping, regardless of the content's aspect ratio.
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    LaunchedEffect(state.streamUrl, state.playToken) {
        val url = state.streamUrl ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (state.resumeFromMs > 0) player.seekTo(state.resumeFromMs)
        player.play()
    }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    // LIVE: the built-in controller's seek bar / timer is useless (you can't scrub
                    // live) — hide it entirely; channel ◀ ▶ buttons are drawn in Compose below.
                    // VOD: keep the touch controller (tap to show, drag seek, ±10s, play/pause).
                    useController = !isLive
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    controllerShowTimeoutMs = 3000
                    // FULL-SCREEN fill on any phone: ZOOM crops the few edge pixels needed to
                    // fill the screen while PRESERVING the aspect ratio — no black bars, no
                    // stretching (client: video must be full screen on mobile).
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    // Hold the last frame while a live channel switch resolves — no black flash.
                    setKeepContentOnPlayerReset(true)
                    // Keep the screen awake while the video plays.
                    keepScreenOn = true
                    // No buffering spinner — video just pops in when ready.
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        // Back + title overlay (top-left).
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }
        if (state.title.isNotBlank()) {
            Text(
                state.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
            )
        }
        // LIVE: channel backward / forward buttons (replaces the pointless seek-bar controller).
        if (isLive) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.switchChannel(-1) }) {
                    Icon(
                        Icons.Filled.SkipPrevious, "Previous channel",
                        tint = Color.White, modifier = Modifier.size(34.dp)
                    )
                }
                Text(
                    state.channelLabel.ifBlank { state.title },
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                IconButton(onClick = { viewModel.switchChannel(1) }) {
                    Icon(
                        Icons.Filled.SkipNext, "Next channel",
                        tint = Color.White, modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
        state.error?.let {
            Text(it, color = Color(0xFFFF8A80), fontSize = 15.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }
    }
}
