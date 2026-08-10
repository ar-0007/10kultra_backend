package com.tenkultra.tv.presentation.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.tenkultra.tv.presentation.common.onTap
import kotlinx.coroutines.delay

/** Seconds a live stream may sit stalled (buffering / frozen position) before auto-recovery. */
private const val LIVE_STALL_TIMEOUT_SECS = 8

/**
 * LIVE TV fullscreen (TV/box) — driven by the dual-player [LivePlayerController], so switching
 * channels NEVER pauses the current one: it keeps playing (video + audio) while the next channel
 * buffers on a hidden player, then swaps in the moment it's ready. ▲/▼ change channel, OK shows
 * the channel card, Back exits to the list.
 */
@OptIn(UnstableApi::class)
@Composable
fun LivePlayerContent(
    onBack: () -> Unit,
    viewModel: PlayerViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val controller = remember {
        LivePlayerController(
            context = context,
            scope = scope,
            resolveLink = viewModel::linkFor,
            warmLink = viewModel::warmLinkFor
        )
    }
    DisposableEffect(Unit) { onDispose { controller.release() } }

    // Saved quality cap (Settings → Video) on both players.
    LaunchedEffect(state.quality) { controller.applyQuality(state.quality) }

    // Tune whenever the selected channel changes (initial open + every ▲/▼ surf). The controller
    // debounces internally, so holding the button coalesces into one tune on the settled channel.
    LaunchedEffect(state.currentIndex) { controller.tuneTo(state.currentIndex) }

    // Stall watchdog: ExoPlayer's error only fires on HARD failures, but the common live freeze
    // is a SILENT stall (token died, segments stop arriving). CRUCIAL nuance: buffering while
    // data IS still arriving (bufferedPosition growing) is just a slow network — a fresh link
    // won't make bandwidth appear, and reconnecting then only interrupts a stream that would
    // have caught up (the "Reconnecting more than before" complaint). Only a DEAD stall — no
    // playback progress AND no data arriving — triggers recovery.
    LaunchedEffect(Unit) {
        var lastPos = -1L
        var lastBuf = -1L
        var stalledSecs = 0
        while (true) {
            delay(1000)
            val p = controller.frontPlayer
            val pos = p.currentPosition
            val buf = p.bufferedPosition
            val advancing = pos != lastPos
            val dataArriving = buf != lastBuf
            lastPos = pos
            lastBuf = buf
            val stalled = when {
                p.playbackState == Player.STATE_BUFFERING -> !dataArriving      // starving, nothing coming
                p.playbackState == Player.STATE_READY && p.playWhenReady -> !advancing && !dataArriving
                else -> false
            }
            if (stalled) {
                if (++stalledSecs >= LIVE_STALL_TIMEOUT_SECS) {
                    controller.recover()
                    stalledSecs = 0
                    lastPos = -1L
                    lastBuf = -1L
                    delay(2500) // give the recovery a moment before judging again
                }
            } else {
                stalledSecs = 0
            }
        }
    }

    // Channel-info card: shows briefly on open and on every channel flip.
    var showChannelInfo by remember { mutableStateOf(false) }
    LaunchedEffect(state.channelSwitch) {
        showChannelInfo = true
        delay(2800)
        showChannelInfo = false
    }

    // App backgrounded → silence BOTH players; back on screen → resume the front one.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> controller.onBackground()
                Lifecycle.Event.ON_RESUME -> controller.onForeground()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onTap("live-player") { showChannelInfo = true }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { viewModel.switchChannel(-1); true }
                    Key.DirectionDown -> { viewModel.switchChannel(1); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                        if (controller.fatalError != null) controller.retryCurrent()
                        else showChannelInfo = true
                        true
                    }
                    // Live has no seeking — swallow left/right so focus can't wander.
                    Key.DirectionLeft, Key.DirectionRight -> true
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    keepScreenOn = true
                    // Hold the last frame across player swaps/resets — never flash black.
                    setKeepContentOnPlayerReset(true)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    player = controller.frontPlayer
                }
            },
            // Re-bind to whichever player is FRONT after each seamless swap.
            update = { view -> if (view.player !== controller.frontPlayer) view.player = controller.frontPlayer },
            modifier = Modifier.fillMaxSize()
        )

        // Mid-stream recovery pill (frozen frame stays up behind it — never black).
        if (controller.reconnecting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 26.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Reconnecting…", color = Color.White, fontSize = 14.sp)
            }
        }

        controller.fatalError?.let { msg ->
            Text(
                text = msg,
                color = Color(0xFFFF6B6B),
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.9f)
                    .padding(24.dp)
            )
        }

        // Animated channel card (slides + fades on each flip).
        AnimatedVisibility(
            visible = showChannelInfo,
            enter = fadeIn(tween(220)) + slideInVertically(tween(280)) { -it },
            exit = fadeOut(tween(220)) + slideOutVertically(tween(260)) { -it },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 26.dp)
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("● LIVE", color = Color(0xFFE53935), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    state.channelLabel,
                    color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "▲ ▼   change channel",
                    color = Color(0xFFC0906F), fontSize = 12.sp
                )
            }
        }
    }
}
