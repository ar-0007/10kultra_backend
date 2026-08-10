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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.tenkultra.tv.domain.model.VideoQuality
import com.tenkultra.tv.presentation.common.onTap
import kotlinx.coroutines.delay

/** Seconds a live stream may sit stalled (buffering / position frozen) before we auto re-resolve. */
private const val STALL_TIMEOUT_SECS = 8

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    // LIVE TV on a TV/box → the dual-player screen (seamless switching: the current channel keeps
    // playing until the next one is ready). VOD / local files (and everything on phones' own
    // MobilePlayerScreen) keep this original single-player path.
    if (viewModel.isLiveStream && viewModel.isTvDevice) {
        LivePlayerContent(onBack = onBack, viewModel = viewModel)
        return
    }
    VodPlayerContent(onBack = onBack, viewModel = viewModel)
}

@OptIn(UnstableApi::class)
@Composable
private fun VodPlayerContent(
    onBack: () -> Unit,
    viewModel: PlayerViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setForceHighestSupportedBitrate(false))
        }
    }

    val isLiveStream = viewModel.isLiveStream
    val player = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Lavf/57.83.100")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            // VOD segments off a cold provider origin have been measured taking ~17s for one 6s
            // chunk. A 15s read timeout aborts mid-segment and restarts the load, which turns a
            // slow start into an endless one. Live keeps the short timeout: there, failing fast
            // and re-resolving is the right recovery.
            .setReadTimeoutMs(if (isLiveStream) 15_000 else 40_000)
        // Live needs a healthy cushion so a brief network dip / power-save hiccup doesn't
        // drain the buffer and freeze the picture; VOD keeps the lean fast-start tuning.
        val loadControl = if (isLiveStream) {
            DefaultLoadControl.Builder()
                // Target a healthy 12s/50s cushion, but START the picture as soon as ~0.6s is
                // buffered so a channel pops in almost instantly on a single forward/back; keep a
                // small resume-after-rebuffer so a blip doesn't stall for long.
                .setBufferDurationsMs(12_000, 50_000, 600, 1_500)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            // VOD on these portals is single-variant HLS (~3.8 Mbps, ~6s segments) served from an
            // origin that starts SLOW and warms up — measured 2.1 Mbps on the first segment, 8 Mbps
            // by the third. Starting playback after ~1s of media meant the picture began on segment
            // one and then stalled again immediately, over and over, because each new 6s chunk was
            // arriving slower than it played.
            //
            // So: start a little later, then buffer FAR ahead (2 minutes) while the origin warms up.
            // The player spends the early slow period filling a cushion instead of stop-starting,
            // and a rebuffer resumes with 5s in hand rather than 2.5s.
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 120_000, 2_500, 5_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)    // ⏪ 10s
            .setSeekForwardIncrementMs(10_000) // ⏩ 10s
            .build()
            .apply {
                playWhenReady = true
                // Keep CPU + Wi-Fi awake during playback so an idle box's power-save
                // doesn't starve the live stream and freeze it after a few minutes.
                setWakeMode(C.WAKE_MODE_NETWORK)
                // VOD seeking: snap to the nearest keyframe instead of an EXACT seek. EXACT
                // forces a full re-decode from the previous keyframe on every ⏪/⏩ — on a
                // weak TV box, rapid repeated EXACT seeks overwhelm the MediaCodec decoder
                // and the app crashes. CLOSEST_SYNC is light and smooth (lands ±a few sec).
                if (!isLiveStream) setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
    }

    // Apply the saved quality cap to the track selector.
    LaunchedEffect(state.quality) {
        val q = state.quality
        trackSelector.setParameters(
            trackSelector.buildUponParameters().apply {
                if (q == VideoQuality.AUTO) clearVideoSizeConstraints()
                else setMaxVideoSize(Int.MAX_VALUE, q.maxHeight)
            }
        )
    }

    // Keyed on playToken too, so an auto re-resolve after a stream error re-prepares the
    // player even when the portal hands back the same URL.
    LaunchedEffect(state.streamUrl, state.playToken) {
        val url = state.streamUrl ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        // Resume movies / series episodes from where they were left off.
        if (state.resumeFromMs > 0) player.seekTo(state.resumeFromMs)
        player.play()
    }

    // Persist watch progress every few seconds (the VM ignores live TV).
    LaunchedEffect(state.streamUrl) {
        if (state.streamUrl == null) return@LaunchedEffect
        while (true) {
            delay(4000)
            viewModel.saveProgress(player.currentPosition, player.duration)
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var controlsRevision by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    // ── Seek accumulation (VOD) ───────────────────────────────────────────────
    // Holding ⏩/⏪ used to fire a fresh seek on EVERY key-repeat — dozens of seeks a
    // second overwhelmed the decoder: huge lag, then the app froze/crashed (and Back
    // during it crashed too). Instead we accumulate a target and commit ONE seek ~350ms
    // after the last press, showing a live preview meanwhile. A hold now feels instant
    // and never overloads the codec.
    var seekTargetMs by remember { mutableStateOf<Long?>(null) }
    fun nudgeSeek(deltaMs: Long) {
        if (state.isLive) return
        val dur = if (durationMs > 0) durationMs else player.duration.coerceAtLeast(0L)
        val base = seekTargetMs ?: player.currentPosition.coerceAtLeast(0L)
        seekTargetMs = (base + deltaMs).coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
        controlsRevision++ // keep the controls/preview visible while scrubbing
    }
    // Commit the accumulated seek once the user stops pressing (debounced).
    LaunchedEffect(seekTargetMs) {
        val target = seekTargetMs ?: return@LaunchedEffect
        delay(350)
        runCatching { player.seekTo(target) }
        positionMs = target
        seekTargetMs = null
    }

    // In-player quality picker (VOD): the available resolutions come from the stream's
    // own video tracks (HLS renditions), so it shows whatever the backend serves (720p/1080p/4K…).
    var availableQualities by remember { mutableStateOf<List<Int>>(emptyList()) }
    var selectedQualityHeight by remember { mutableStateOf(0) } // 0 = Auto
    var showQualityMenu by remember { mutableStateOf(false) }
    var qualityMenuIndex by remember { mutableStateOf(0) }
    val qualityOptions = remember(availableQualities) { listOf(0) + availableQualities }

    fun applyQuality(height: Int) {
        selectedQualityHeight = height
        trackSelector.setParameters(
            trackSelector.buildUponParameters().apply {
                if (height <= 0) clearVideoSizeConstraints() else setMaxVideoSize(Int.MAX_VALUE, height)
            }
        )
    }
    fun openQualityMenu() {
        if (qualityOptions.size <= 1) return
        qualityMenuIndex = qualityOptions.indexOf(selectedQualityHeight).coerceAtLeast(0)
        showQualityMenu = true
    }

    // Tick the position/duration for the progress bar.
    LaunchedEffect(state.streamUrl) {
        if (state.streamUrl == null) return@LaunchedEffect
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Live-TV stall watchdog: ExoPlayer's onPlayerError fires only on a HARD error, but the
    // most common live freeze is a SILENT stall — token expires / segments stop arriving and
    // the player just sits in BUFFERING (or "playing" but the position stops advancing) with
    // no error. Detect that and trigger an auto re-resolve (which swaps in the warm link).
    LaunchedEffect(state.streamUrl, state.isLive) {
        if (state.streamUrl == null || !state.isLive) return@LaunchedEffect
        var lastPos = -1L
        var stalledSecs = 0
        while (true) {
            delay(1000)
            val pos = player.currentPosition
            val buffering = player.playbackState == Player.STATE_BUFFERING
            val advancing = pos > lastPos
            lastPos = pos
            val stalled = buffering || (player.playWhenReady && !advancing &&
                player.playbackState == Player.STATE_READY)
            if (stalled) {
                stalledSecs++
                if (stalledSecs >= STALL_TIMEOUT_SECS) {
                    viewModel.onPlaybackError() // fresh/warm link + re-prepare
                    stalledSecs = 0
                    lastPos = -1L
                    delay(2500) // give the re-prepare a moment before judging again
                }
            } else {
                stalledSecs = 0
            }
        }
    }

    // Live TV: briefly show the channel-info card on open and on every channel flip.
    var showChannelInfo by remember { mutableStateOf(false) }
    LaunchedEffect(state.channelSwitch, state.isLive) {
        if (state.isLive) {
            showChannelInfo = true
            delay(2800)
            showChannelInfo = false
        }
    }

    DisposableEffect(player) {
        // Count consecutive "behind live window" errors — if a simple seek-to-live keeps
        // failing, the playlist/token is actually dead, so escalate to a fresh-link re-resolve.
        var behindLiveStreak = 0
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    behindLiveStreak = 0
                    viewModel.onPlaybackResumed() // healthy stream — reset the retry budget
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && behindLiveStreak < 2) {
                    // Fell behind the live edge — jump back to the live point and retry, no re-fetch needed.
                    behindLiveStreak++
                    player.seekToDefaultPosition()
                    player.prepare()
                } else {
                    // Expired token / dropped segment / repeated behind-live — fetch a fresh link and retry.
                    behindLiveStreak = 0
                    viewModel.onPlaybackError()
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                availableQualities = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO }
                    .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).height } }
                    .filter { it > 0 }
                    .distinct()
                    .sortedDescending()
            }
        }
        player.addListener(listener)
        onDispose {
            // Defensive: if a seek/rebuffer is still in flight when the user hits Back,
            // touching the player can throw — never let teardown crash the app.
            runCatching { viewModel.saveProgress(player.currentPosition, player.duration) }
            runCatching { player.removeListener(listener) }
            runCatching { player.release() }
        }
    }

    // Reveal the controls overlay on any interaction, then auto-hide after ~3s.
    LaunchedEffect(controlsRevision) {
        showControls = true
        delay(3000)
        showControls = false
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Stop audio when the app is backgrounded (e.g. the HOME button) — not just on Back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            // Touch/mouse: first tap reveals controls; tapping again toggles play/pause.
            .onTap("player") {
                if (showControls) {
                    if (player.isPlaying) player.pause() else player.play()
                }
                controlsRevision++
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Quality menu owns the keys while open.
                if (showQualityMenu) {
                    when (event.key) {
                        Key.DirectionUp -> if (qualityMenuIndex > 0) qualityMenuIndex--
                        Key.DirectionDown -> if (qualityMenuIndex < qualityOptions.lastIndex) qualityMenuIndex++
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            applyQuality(qualityOptions[qualityMenuIndex]); showQualityMenu = false
                        }
                        else -> showQualityMenu = false
                    }
                    return@onPreviewKeyEvent true
                }
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.MediaPlayPause -> {
                        // Live TV has no play/pause — OK just reveals the controls overlay.
                        if (!state.isLive) { if (player.isPlaying) player.pause() else player.play() }
                        controlsRevision++; true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        // Live: left/right do nothing (channels change with up/down). VOD: seek −10s
                        // (accumulated — a hold collapses into ONE seek when you let go).
                        if (!state.isLive) nudgeSeek(-10_000); true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        if (!state.isLive) nudgeSeek(10_000); true
                    }
                    Key.DirectionUp -> {
                        // Live: previous channel. VOD: open the quality picker (1080p/4K…).
                        if (state.isLive) viewModel.switchChannel(-1) else openQualityMenu()
                        controlsRevision++; true
                    }
                    Key.DirectionDown -> { if (state.isLive) viewModel.switchChannel(1); controlsRevision++; true }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false // playback is driven by the D-pad handler above
                    // Fill the whole screen — no black bars (movies/series run full-screen).
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    // Keep the screen on during playback so an idle box doesn't dim/sleep.
                    keepScreenOn = true
                    // SEAMLESS channel switch: hold the CURRENT channel's last frame on screen while
                    // the next channel resolves + buffers, so switching never flashes black.
                    setKeepContentOnPlayerReset(true)
                    // No buffering spinner — the video just pops in when ready (client: no spinner).
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Title + quality overlay (top-left) — VOD only; live uses the centered channel card.
        if (state.title.isNotBlank() && !state.isLive) {
            Column(modifier = Modifier.align(Alignment.TopStart).padding(20.dp)) {
                Text(state.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Quality: ${state.quality.label}  ·  OK = play/pause   ‹ −10s   +10s ›",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }

        // Live channel switches stay clean — no "Starting stream…" / tuning text or spinner.
        if (state.loading && !state.isLive) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text("Starting stream…", color = Color.White, fontSize = 16.sp)
            }
        }

        // Live auto-recovery: a subtle pill instead of a frozen frame while a fresh link loads.
        if (state.reconnecting) {
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

        state.error?.let { msg ->
            // A multi-line play diagnostic needs to be readable (long request URL) — smaller, wrapped.
            val isDiagnostic = msg.contains("— diagnostic")
            Text(
                text = msg,
                color = Color(0xFFFF6B6B),
                fontSize = if (isDiagnostic) 13.sp else 16.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.9f)
                    .padding(24.dp)
            )
        }

        // Scrub preview (VOD): while a ⏩/⏪ hold accumulates, show the target time big
        // in the center so the viewer sees where they'll land before the seek commits.
        seekTargetMs?.let { target ->
            if (!state.isLive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(
                        "${formatTime(target)}  /  ${formatTime(durationMs)}",
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Live TV has NO player bar — just the channel-info card + ▲▼ to change channel.
        // The seek/transport bar shows for VOD only.
        if (showControls && !state.loading && state.error == null && !state.isLive) {
            ControlsBar(
                isLive = state.isLive,
                isPlaying = isPlaying,
                // While scrubbing, show the pending target so the bar tracks the hold live.
                positionMs = seekTargetMs ?: positionMs,
                durationMs = durationMs,
                onRewind = {
                    if (state.isLive) viewModel.switchChannel(-1) else nudgeSeek(-10_000)
                    controlsRevision++
                },
                onPlayPause = { if (player.isPlaying) player.pause() else player.play(); controlsRevision++ },
                onForward = {
                    if (state.isLive) viewModel.switchChannel(1) else nudgeSeek(10_000)
                    controlsRevision++
                },
                onSeekTo = { ms -> runCatching { player.seekTo(ms) }; positionMs = ms; controlsRevision++ },
                onQuality = { openQualityMenu(); controlsRevision++ },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.82f)
                    .padding(bottom = 40.dp)
            )
        }

        // Quality picker overlay (VOD) — resolutions from the stream itself.
        if (showQualityMenu) {
            QualityMenu(
                options = qualityOptions,
                menuIndex = qualityMenuIndex,
                currentHeight = selectedQualityHeight,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp)
            )
        }

        // Live TV: animated channel card (slides + fades in on each flip).
        AnimatedVisibility(
            visible = showChannelInfo && state.isLive,
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

@Composable
private fun ControlsBar(
    isLive: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onQuality: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        if (isLive) {
            // Live: a "LIVE" badge, no seek bar (you can't scrub a live stream).
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFE53935)))
                Spacer(Modifier.width(7.dp))
                Text("LIVE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(1.dp).weight(1f))
                Text("▲ ▼  change channel", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
        } else {
            // VOD: elapsed / total + remaining, then the seek bar.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(positionMs), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("/ ${formatTime(durationMs)}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.width(1.dp).weight(1f))
                Text("-${formatTime(remainingMs)} left", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            SeekBar(fraction = fraction, durationMs = durationMs, onSeekTo = onSeekTo)
            Spacer(Modifier.height(10.dp))
        }
        // Transport controls. Live: prev/next channel only — NO play/pause. VOD: ⏪10 ▶/⏸ ⏩10.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLive) {
                Icon(
                    Icons.Filled.SkipPrevious, "Previous channel",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp).onTap("rew") { onRewind() }
                )
                Spacer(Modifier.width(48.dp))
                Icon(
                    Icons.Filled.SkipNext, "Next channel",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp).onTap("fwd") { onForward() }
                )
            } else {
                Icon(
                    Icons.Filled.Replay10, "Rewind 10s",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp).onTap("rew") { onRewind() }
                )
                Spacer(Modifier.width(30.dp))
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp).onTap("pp") { onPlayPause() }
                )
                Spacer(Modifier.width(30.dp))
                Icon(
                    Icons.Filled.Forward10, "Forward 10s",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp).onTap("fwd") { onForward() }
                )
                Spacer(Modifier.width(30.dp))
                Icon(
                    Icons.Filled.HighQuality, "Quality (▲)",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp).onTap("quality") { onQuality() }
                )
            }
        }
    }
}

/** Resolution picker — "Auto" plus whatever heights the stream actually offers. */
@Composable
private fun QualityMenu(
    options: List<Int>,
    menuIndex: Int,
    currentHeight: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(230.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE6000000))
            .padding(vertical = 10.dp, horizontal = 8.dp)
    ) {
        Text(
            "Quality",
            color = Color(0xFFC0906F), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 10.dp, bottom = 6.dp)
        )
        options.forEachIndexed { i, h ->
            val highlighted = i == menuIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (highlighted) Color(0x33C0906F) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (h == currentHeight) "●  " else "    ",
                    color = Color(0xFFC0906F), fontSize = 13.sp
                )
                Text(
                    qualityLabel(h),
                    color = if (highlighted) Color.White else Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        Text(
            "▲ ▼ select · OK apply",
            color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp,
            modifier = Modifier.padding(start = 10.dp, top = 6.dp)
        )
    }
}

private fun qualityLabel(height: Int): String = when {
    height <= 0 -> "Auto"
    height >= 2160 -> "4K · 2160p"
    height >= 1440 -> "1440p"
    height >= 1080 -> "1080p · FHD"
    height >= 720 -> "720p · HD"
    height >= 480 -> "480p"
    else -> "${height}p"
}

/** Clickable / draggable seek bar — tap or drag anywhere to jump to that point. */
@Composable
private fun SeekBar(fraction: Float, durationMs: Long, onSeekTo: (Long) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                detectTapGestures { offset ->
                    val f = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeekTo((f * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    val f = (change.position.x / size.width).coerceIn(0f, 1f)
                    onSeekTo((f * durationMs).toLong())
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val barWidth = maxWidth
        // Track
        Box(
            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.25f))
        )
        // Filled portion
        Box(
            Modifier.fillMaxWidth(fraction).height(5.dp).clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFC0906F))
        )
        // Draggable thumb
        Box(
            Modifier
                .offset(x = barWidth * fraction - 7.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** ms → "M:SS" or "H:MM:SS". */
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
