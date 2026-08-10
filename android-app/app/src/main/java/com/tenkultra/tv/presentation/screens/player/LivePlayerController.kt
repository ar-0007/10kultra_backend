package com.tenkultra.tv.presentation.screens.player

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.LoadControl
import com.tenkultra.tv.domain.model.VideoQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A LoadControl that caps how much a player may buffer while it's the hidden BACK player.
 * Without this, a pre-buffering back player downloads up to maxBuffer of a second live stream
 * in the background — competing for bandwidth with the on-screen channel that just started on
 * a thin cushion (stutter + reconnect spam). FRONT role = full cushion; BACK role = a small
 * swap-ready cushion only.
 *
 * Buffer numbers double as the OOM guard: TWO players with unbounded 50s buffers exhausted the
 * Java heap and crashed the app mid-playback (the "app goes back to home by itself" bug —
 * Android relaunched the crashed app). maxBuffer 20s + a HARD 16MB byte cap per player (byte
 * cap takes priority) keeps both players well under the smallest box's heap.
 *
 * NOTE: implemented as a DefaultLoadControl SUBCLASS — interface delegation (`by`) does NOT
 * forward Java default methods, so media3's deprecated-default shims threw
 * "getBackBufferDurationUs not implemented" at player construction.
 */
@UnstableApi
private class RoleLoadControl(
    allocator: androidx.media3.exoplayer.upstream.DefaultAllocator
) : DefaultLoadControl(
    allocator,
    /* minBufferMs = */ 12_000,
    /* maxBufferMs = */ 20_000,
    /* bufferForPlaybackMs = */ 500,
    /* bufferForPlaybackAfterRebufferMs = */ 1_500,
    /* targetBufferBytes = */ TARGET_BUFFER_BYTES,
    /* prioritizeTimeOverSizeThresholds = */ false, // the byte cap WINS — OOM-proof
    /* backBufferDurationMs = */ DEFAULT_BACK_BUFFER_DURATION_MS,
    /* retainBackBufferFromKeyframe = */ DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME
) {
    /** true while this player is the hidden pre-buffer — loading stops at [BACK_CAP_US]. */
    @Volatile
    var isBack: Boolean = true

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        if (isBack && parameters.bufferedDurationUs >= BACK_CAP_US) return false
        return super.shouldContinueLoading(parameters)
    }

    private companion object {
        // Enough for an instant, stutter-free swap — but never a bandwidth hog.
        const val BACK_CAP_US = 6_000_000L // 6s
        // Hard per-player buffer cap in BYTES.
        const val TARGET_BUFFER_BYTES = 16 * 1024 * 1024
    }
}

/**
 * Dual-player ("double buffer") engine for LIVE TV — the fix for slow / pausing channel switches.
 *
 * A single ExoPlayer MUST reset on prepare(), so with one player every switch stopped the current
 * channel and buffered the next from zero (1–4s of frozen frame / black). Here TWO players
 * ping-pong:
 *  - FRONT is bound to the PlayerView and KEEPS PLAYING (video + audio) the current channel.
 *  - BACK silently resolves + pre-buffers the next channel with playWhenReady=false and NO
 *    surface attached — so only ONE hardware video decoder runs (safe on weak boxes) while the
 *    slow part (segment download) still happens in the background.
 *  - The instant BACK hits STATE_READY we swap: front pauses (its media is KEPT, so flipping
 *    back to the channel you just left is instant), back starts playing, the PlayerView re-binds.
 *
 * Dead cached links (the "loads first time, not the second time" bug) fail on the HIDDEN back
 * player and are retried with a FRESH link invisibly — the viewer keeps watching the old channel
 * the whole time and never sees the failure.
 *
 * After each successful swap the now-idle back player pre-buffers the next channel in the user's
 * surf DIRECTION, so a continued single-step surf swaps instantly ("INSTANT (pre-buffered)" in
 * `adb logcat -s UltraLive`).
 */
@UnstableApi
class LivePlayerController(
    context: Context,
    private val scope: CoroutineScope,
    /** Resolves a playable link for the channel at [index]; fresh=true bypasses the link cache. */
    private val resolveLink: suspend (index: Int, fresh: Boolean) -> String?,
    /** Link-only warm into the shared cache (no player/session) — keeps a neighbour's link hot. */
    private val warmLink: suspend (index: Int) -> Unit = {}
) {
    private val selectorA = DefaultTrackSelector(context)
    private val selectorB = DefaultTrackSelector(context)

    // ONE allocator SHARED by both players: freed segments from one are reused by the other
    // instead of two independently growing pools — half the peak heap of two separate pools.
    private val sharedAllocator =
        androidx.media3.exoplayer.upstream.DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)

    private val loadControlA = RoleLoadControl(sharedAllocator)
    private val loadControlB = RoleLoadControl(sharedAllocator)
    val playerA: ExoPlayer = build(context, selectorA, loadControlA)
    val playerB: ExoPlayer = build(context, selectorB, loadControlB)

    /** Which player is on screen. Compose state so the PlayerView re-binds on swap. */
    var frontIsA by mutableStateOf(true)
        private set
    val frontPlayer: ExoPlayer get() = if (frontIsA) playerA else playerB
    private val backPlayer: ExoPlayer get() = if (frontIsA) playerB else playerA

    /** Mid-stream drop being recovered — the screen shows the "Reconnecting…" pill. */
    var reconnecting by mutableStateOf(false)
        private set

    /** All retries exhausted — the screen shows this and OK retries. */
    var fatalError by mutableStateOf<String?>(null)
        private set

    private var activeJob: Job? = null
    private var prefetchJob: Job? = null

    // Which channel index each player currently HOLDS media for, and since when. Lets a tune
    // reuse an already-buffered player (instant swap) and keeps the just-left channel warm.
    private var heldA = -1
    private var heldB = -1
    private var heldAtA = 0L
    private var heldAtB = 0L

    private var currentIndex = -1
    private var lastDelta = 1
    private var retryBudget = 0
    private var behindLiveStreak = 0
    private var foreground = true

    init {
        // Front-player health: reset the retry budget when playback is healthy; self-heal errors.
        listOf(playerA, playerB).forEach { p ->
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    if (p === frontPlayer && playing) {
                        behindLiveStreak = 0
                        retryBudget = 0
                        reconnecting = false
                        fatalError = null
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    // Back-player errors are handled by the tune loop (invisible fresh retry).
                    if (p !== frontPlayer) return
                    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW &&
                        behindLiveStreak < 2
                    ) {
                        // Fell behind the live window — jump to the live edge, no re-fetch needed.
                        behindLiveStreak++
                        p.seekToDefaultPosition()
                        p.prepare()
                    } else {
                        behindLiveStreak = 0
                        recover()
                    }
                }
            })
        }
    }

    private fun build(context: Context, selector: DefaultTrackSelector, loadControl: LoadControl): ExoPlayer {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Lavf/57.83.100")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = false
                setWakeMode(C.WAKE_MODE_NETWORK)
            }
    }

    /** Caps both players' max resolution (Settings → Video). */
    fun applyQuality(quality: VideoQuality) {
        listOf(selectorA, selectorB).forEach { sel ->
            sel.setParameters(
                sel.buildUponParameters().apply {
                    if (quality == VideoQuality.AUTO) clearVideoSizeConstraints()
                    else setMaxVideoSize(Int.MAX_VALUE, quality.maxHeight)
                }
            )
        }
    }

    /**
     * Tunes to a channel. The CURRENT channel keeps playing untouched while the new one resolves
     * and buffers on the hidden back player; only when it's READY does the swap happen. Rapid
     * surfing is debounced — every new call cancels the pending tune (and any prefetch, so a
     * stale setMediaItem can never race the tune — the "wrong channel" bug from the review).
     */
    fun tuneTo(index: Int) {
        if (index == currentIndex) return
        val initial = currentIndex < 0
        if (!initial) lastDelta = if (index > currentIndex) 1 else -1
        currentIndex = index
        retryBudget = 0
        fatalError = null
        activeJob?.cancel()
        prefetchJob?.cancel()
        activeJob = scope.launch {
            if (!initial) delay(SWITCH_DEBOUNCE_MS)
            runTune(index, forceFresh = false, allowInstant = true, isRecover = false)
        }
    }

    /**
     * Mid-stream drop (stall watchdog / hard error): fetch a FRESH link on the back player and
     * swap when ready. The frozen frame stays up (better than black) under the reconnect pill.
     */
    fun recover() {
        if (reconnecting) return // one recovery at a time; the watchdog re-fires if it fails
        if (retryBudget >= MAX_RECOVER_RETRIES) {
            fatalError = "Channel unavailable. Press ▲ ▼ to change channel, or OK to retry."
            return
        }
        retryBudget++
        reconnecting = true
        activeJob?.cancel()
        prefetchJob?.cancel()
        val backoff = 300L * retryBudget
        activeJob = scope.launch {
            delay(backoff)
            runTune(currentIndex, forceFresh = true, allowInstant = false, isRecover = true)
        }
    }

    /** OK on the fatal-error screen: full fresh retry of the current channel. */
    fun retryCurrent() {
        retryBudget = 0
        fatalError = null
        activeJob?.cancel()
        prefetchJob?.cancel()
        activeJob = scope.launch {
            runTune(currentIndex, forceFresh = true, allowInstant = false, isRecover = false)
        }
    }

    private suspend fun runTune(index: Int, forceFresh: Boolean, allowInstant: Boolean, isRecover: Boolean) {
        val bk = backPlayer
        // INSTANT path: the back player already holds this channel buffered & fresh (direction
        // prefetch, or flipping back to the channel just left) → swap immediately, zero wait.
        if (allowInstant && heldIndexOf(bk) == index && bk.playbackState == Player.STATE_READY &&
            System.currentTimeMillis() - heldAtOf(bk) < HELD_FRESH_MS
        ) {
            Log.d("UltraLive", "switch idx=$index INSTANT (pre-buffered)")
            swapTo(index)
            return
        }

        val started = System.currentTimeMillis()
        var attempt = 0
        while (scope.isActive && attempt < MAX_TUNE_ATTEMPTS) {
            // First try may reuse the warmed/cached link (instant); retries always go fresh —
            // the cached link is the one that just failed (dead unplayed Stalker token).
            val fresh = forceFresh || attempt > 0
            val url = runCatching { resolveLink(index, fresh) }.getOrNull()
            if (url.isNullOrBlank()) {
                attempt++
                delay(300)
                continue
            }
            bk.setMediaItem(MediaItem.fromUri(url))
            bk.playWhenReady = false // buffer silently — no audio until the swap
            bk.prepare()
            setHeld(bk, index)

            val deadline = System.currentTimeMillis() + TUNE_WAIT_MS
            var readyAt = 0L
            while (scope.isActive) {
                if (bk.playerError != null) break // dead link → fresh retry (invisible, on BACK)
                if (bk.playbackState == Player.STATE_READY) {
                    if (readyAt == 0L) readyAt = System.currentTimeMillis()
                    // Don't swap on the paper-thin first-READY buffer (~0.5s) — that stuttered
                    // right after every switch. The OLD channel is still playing, so wait for a
                    // healthy swap cushion; fall through anyway if it's just not accumulating.
                    if (bk.totalBufferedDuration >= SWAP_MIN_BUFFER_MS ||
                        System.currentTimeMillis() - readyAt >= SWAP_READY_FALLBACK_MS
                    ) {
                        Log.d("UltraLive", "switch idx=$index ready in ${System.currentTimeMillis() - started}ms")
                        swapTo(index)
                        return
                    }
                }
                if (System.currentTimeMillis() > deadline) break // stuck buffering → fresh link
                delay(100)
            }
            runCatching { bk.stop() }
            attempt++
        }
        if (!scope.isActive) return
        reconnecting = false
        if (isRecover) {
            recover() // escalate with backoff until the budget runs out
        } else {
            fatalError = "Channel unavailable. Press ▲ ▼ to change channel, or OK to retry."
        }
    }

    /**
     * Swap the ready back player on screen. The old front is STOPPED (not just paused) — a paused
     * ExoPlayer keeps downloading up to maxBuffer (50s) on its open CDN session, and Stalker
     * portals cap concurrent sessions per MAC: leaving old sessions running is why "3 channels
     * play fine in a row, the 4th stops". Stopping keeps at most TWO sessions alive (the playing
     * channel + the direction pre-buffer), safely under the portal's limit.
     */
    private fun swapTo(index: Int) {
        val old = frontPlayer
        val neo = backPlayer
        setHeld(neo, index)
        // Role flip: the new FRONT gets the full live cushion; the new BACK is capped so its
        // next pre-buffer can never starve the on-screen stream's bandwidth.
        (if (neo === playerA) loadControlA else loadControlB).isBack = false
        (if (old === playerA) loadControlA else loadControlB).isBack = true
        if (foreground) neo.playWhenReady = true
        frontIsA = !frontIsA
        runCatching { old.stop() }
        setHeld(old, -1) // its buffered media is gone — never instant-swap back to it
        reconnecting = false
        fatalError = null
        startPrefetch(index + lastDelta)
    }

    /**
     * Direction-aware pre-buffer: after a swap the idle player warms the channel the user is
     * most likely to press next (same surf direction) — resolve + prepare, no surface, silent.
     * Cancelled by any new tune/recover BEFORE it can touch the player (no setMediaItem race).
     */
    private fun startPrefetch(nextIndex: Int) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            delay(PREFETCH_DELAY_MS) // the just-swapped channel gets the wire first
            // 1) SURF DIRECTION: fully pre-buffer the likely next channel on the idle player —
            //    a continued surf then swaps INSTANTLY.
            if (nextIndex >= 0) {
                val url = runCatching { resolveLink(nextIndex, false) }.getOrNull()
                if (!isActive) return@launch
                if (url != null) {
                    val bk = backPlayer
                    bk.setMediaItem(MediaItem.fromUri(url))
                    bk.playWhenReady = false
                    bk.prepare()
                    setHeld(bk, nextIndex)
                }
            }
            // 2) OPPOSITE direction: warm just the LINK into the cache (create_link only — no
            //    player, no CDN session), so turning back is a zero-round-trip resolve too.
            val backIndex = currentIndex - lastDelta
            if (backIndex >= 0) runCatching { warmLink(backIndex) }
        }
    }

    private fun heldIndexOf(p: ExoPlayer): Int = if (p === playerA) heldA else heldB
    private fun heldAtOf(p: ExoPlayer): Long = if (p === playerA) heldAtA else heldAtB
    private fun setHeld(p: ExoPlayer, index: Int) {
        if (p === playerA) { heldA = index; heldAtA = System.currentTimeMillis() }
        else { heldB = index; heldAtB = System.currentTimeMillis() }
    }

    /** Lifecycle: app hidden → pause BOTH (a mid-tune swap must not blast audio in background). */
    fun onBackground() {
        foreground = false
        playerA.playWhenReady = false
        playerB.playWhenReady = false
    }

    /** Lifecycle: back on screen → resume the FRONT player only (back stays a silent buffer). */
    fun onForeground() {
        foreground = true
        frontPlayer.playWhenReady = true
    }

    fun release() {
        activeJob?.cancel()
        prefetchJob?.cancel()
        runCatching { playerA.release() }
        runCatching { playerB.release() }
    }

    private companion object {
        // Debounce before tuning. Tiny (50ms) so a SINGLE prev/next press starts resolving almost
        // immediately — a rapid surf still coalesces because every new press CANCELS the pending
        // tune (and any in-flight resolve) before it can matter, and all the work happens on the
        // hidden back player anyway (the on-screen channel is never disturbed).
        const val SWITCH_DEBOUNCE_MS = 50L
        // Fresh-link attempts per tune before giving up.
        const val MAX_TUNE_ATTEMPTS = 3
        // Mid-stream recovery budget (live tokens rotate — heal aggressively).
        const val MAX_RECOVER_RETRIES = 6
        // Max wait for one link to reach READY before trying a fresh one.
        const val TUNE_WAIT_MS = 8_000L
        // A held (pre-buffered) player is only swap-reusable this long — older and its live
        // position is too far behind the edge (BehindLiveWindow churn), so tune normally instead.
        const val HELD_FRESH_MS = 8_000L
        // Let the just-swapped channel build its OWN cushion first before the neighbour
        // pre-buffer starts sharing the wire.
        const val PREFETCH_DELAY_MS = 600L
        // Swap only once the new channel holds a healthy cushion — the old channel keeps playing
        // during this wait, so it costs nothing visually and kills the post-swap stutter.
        const val SWAP_MIN_BUFFER_MS = 2_500L
        // …but if the buffer just isn't accumulating (very slow stream), swap anyway after this
        // long at READY rather than keeping the user on the old channel forever.
        const val SWAP_READY_FALLBACK_MS = 2_000L
    }
}
