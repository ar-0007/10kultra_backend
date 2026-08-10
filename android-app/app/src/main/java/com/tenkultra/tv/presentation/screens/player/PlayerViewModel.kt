package com.tenkultra.tv.presentation.screens.player

import android.net.Uri
import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenkultra.tv.data.api.LivePlaybackContext
import com.tenkultra.tv.data.api.VodPlaybackContext
import com.tenkultra.tv.data.datastore.ContinueWatchingStore
import com.tenkultra.tv.data.datastore.PlaybackProgressStore
import com.tenkultra.tv.data.datastore.SettingsDataStore
import com.tenkultra.tv.data.datastore.WatchedEntry
import com.tenkultra.tv.data.repository.ContentRepository
import com.tenkultra.tv.domain.model.VideoQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class PlayerUiState(
    val title: String = "",
    val streamUrl: String? = null,
    val quality: VideoQuality = VideoQuality.AUTO,
    val resumeFromMs: Long = 0L,
    val isLive: Boolean = false,
    val channelLabel: String = "",
    /** The selected live channel's index — the dual-player controller tunes to this. */
    val currentIndex: Int = 0,
    val channelSwitch: Int = 0,
    val loading: Boolean = true,
    /** A deliberate channel change is resolving — distinct from [reconnecting]; shows no error/pill. */
    val switching: Boolean = false,
    val reconnecting: Boolean = false,
    val error: String? = null,
    /** Bumped on every successful (re)resolve so the player re-prepares even if the URL is unchanged. */
    val playToken: Int = 0
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val settings: SettingsDataStore,
    private val progressStore: PlaybackProgressStore,
    private val continueWatching: ContinueWatchingStore,
    private val liveContext: LivePlaybackContext,
    private val vodContext: VodPlaybackContext,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * TV/box → the dual-player [LivePlayerController] owns live resolving/playback; this VM only
     * tracks the selected channel. Phones keep the original VM-driven resolve path (single player
     * with touch controls).
     */
    val isTvDevice: Boolean by lazy {
        val ui = appContext.getSystemService(android.content.Context.UI_MODE_SERVICE)
            as? android.app.UiModeManager
        ui?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
            appContext.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    }

    private val type: String = savedStateHandle["type"] ?: "itv"
    private val cmd: String = decode(savedStateHandle["cmd"])
    private val title: String = decode(savedStateHandle["title"])
    private val series: Int = (savedStateHandle["series"] as? String)?.toIntOrNull() ?: 0

    /** Movies + series episodes (and local files) resume; live TV never does. */
    private val isResumable: Boolean = type == "vod" || type == "file"
    private val progressKey: String = "$type:$cmd:$series"

    private val isLive: Boolean = type == "itv"
    /** Exposed so the player can pick a live-tuned buffer/wake config. */
    val isLiveStream: Boolean get() = isLive
    private var currentCmd: String = cmd
    private var liveIndex: Int = liveContext.index

    /** How a resolve was triggered — drives both the UI state shown and the link-fetch strategy. */
    private enum class Tune { INITIAL, SWITCH, RECONNECT, SILENT_RETRY }

    /**
     * Level-B proactive token refresh: for live channels we keep a FRESH stream link
     * pre-fetched in the background (Stalker create_link tokens are short-lived). When the
     * playing token finally dies, recovery swaps in this already-warm link instantly — no
     * portal round-trip during the gap — so the stream resumes near-seamlessly.
     */
    @Volatile
    private var warmLink: String? = null
    private var warmJob: Job? = null

    // ── Fast, race-free channel switching ────────────────────────────────────
    // The single in-flight resolve. Cancelled on every new tune so only the latest wins.
    private var resolveJob: Job? = null
    // Debounces rapid Next/Prev surfing: only the channel the user SETTLES on is resolved.
    private var switchJob: Job? = null
    // Warms the next/prev channel links so a single-step surf is an instant cache hit.
    private var prefetchJob: Job? = null
    // Monotonic tune id — a resolve applies its result ONLY if it's still the latest (kills the
    // stale-response race that surfaced the wrong channel / a black screen during fast surfing).
    private var tuneGen: Int = 0
    // Cached once — avoids a DataStore read on every single channel switch.
    @Volatile
    private var cachedQuality: VideoQuality? = null
    // True once the CURRENT tune has actually rendered — lets us tell an initial-tune failure
    // (retry silently with a fresh link) from a genuine mid-stream drop (show "Reconnecting…").
    @Volatile
    private var hasStartedPlaying = false

    private fun labelFor(index: Int): String =
        liveContext.channelAt(index)?.let {
            (if (it.number.isNotBlank()) "${it.number}  ·  " else "") + it.name
        } ?: title

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            title = title,
            isLive = isLive,
            channelLabel = if (isLive) labelFor(liveIndex) else "",
            currentIndex = liveIndex
        )
    )
    val uiState = _uiState.asStateFlow()

    /** How many times we've auto-refetched the link after a playback error (reset once it plays). */
    private var retryCount = 0

    init {
        if (isLive && isTvDevice) {
            // Live on TV: the dual-player controller resolves + plays; just load the quality cap.
            viewModelScope.launch {
                val q = settings.videoQualityFlow.first().also { cachedQuality = it }
                _uiState.update { it.copy(quality = q, loading = false) }
            }
        } else {
            resolve(Tune.INITIAL)
        }
    }

    /**
     * Resolves a playable link for the live channel at [index] — used by the dual-player
     * controller. fresh=true bypasses the short link cache (for retries — the cached link is the
     * dead one). Falls back to the route's own cmd if the hand-off list is gone (process death).
     */
    suspend fun linkFor(index: Int, fresh: Boolean): String? {
        val chCmd = liveContext.channelAt(index)?.cmd
            ?: cmd.takeIf { liveContext.channels.isEmpty() && it.isNotBlank() }
            ?: return null
        // consume=true: this link goes straight into a player (tune or pre-buffer both download
        // segments = token consumed), so it must leave the cache and never be handed out again.
        return contentRepository.createStreamLink("itv", chCmd, null, useCache = !fresh, consume = true)
            .getOrNull()
    }

    /**
     * Resolves a playable stream link and hands it to the player. Every call CANCELS the previous
     * in-flight resolve and stamps a new [tuneGen]; a late/stale result whose generation no longer
     * matches is dropped, so rapid switching can never surface the wrong channel.
     */
    private fun resolve(mode: Tune) {
        val myGen = ++tuneGen
        resolveJob?.cancel()
        _uiState.update {
            it.copy(
                loading = mode == Tune.INITIAL && it.streamUrl == null,
                switching = mode == Tune.SWITCH || mode == Tune.SILENT_RETRY,
                reconnecting = mode == Tune.RECONNECT,
                error = null
            )
        }
        resolveJob = viewModelScope.launch {
            val quality = cachedQuality ?: settings.videoQualityFlow.first().also { cachedQuality = it }
            val resume = if (isResumable) progressStore.get(progressKey) else 0L
            if (type == "file") {
                val uri = Uri.fromFile(File(currentCmd)).toString()
                if (myGen == tuneGen) _uiState.update {
                    it.copy(streamUrl = uri, quality = quality, resumeFromMs = resume,
                        loading = false, switching = false, reconnecting = false, playToken = it.playToken + 1)
                }
                return@launch
            }
            // A warm pre-fetched link is used ONLY for a genuine mid-stream reconnect (instant swap).
            val warm = if (mode == Tune.RECONNECT) warmLink else null
            warmLink = null
            // Reuse the short-lived link cache for a normal tune/switch (the channel-list preview and
            // the adjacent-prefetch put a fresh link there → instant). A reconnect / silent-retry must
            // fetch FRESH — the cached link is the one that just died.
            val useCache = mode == Tune.INITIAL || mode == Tune.SWITCH
            val linkResult = if (warm != null) Result.success(warm)
            else contentRepository.createStreamLink(
                type, currentCmd, series.takeIf { it > 0 }, useCache = useCache, consume = true
            )
            // Superseded by a newer tune while we were on the network → drop this result untouched.
            if (myGen != tuneGen || !isActive) return@launch
            linkResult.fold(
                onSuccess = { url ->
                    _uiState.update {
                        it.copy(streamUrl = url, quality = quality, resumeFromMs = resume,
                            loading = false, switching = false, reconnecting = false, playToken = it.playToken + 1)
                    }
                    if (isLive) { startWarmKeeper(); prefetchAdjacent() }
                },
                onFailure = { e ->
                    if (e is CancellationException || myGen != tuneGen) return@fold
                    val msg = e.message.orEmpty()
                    val friendly = when {
                        // The VOD/live play diagnostic (raw portal response) is shown VERBATIM so a
                        // remote box's play failure can be screenshotted and diagnosed.
                        msg.contains("— diagnostic") -> msg
                        msg.contains("Authorization failed", true) ->
                            "Session expired. Press OK to retry."
                        msg.contains("BEGIN_OBJECT") || msg.contains("JSON", true) || msg.contains("gson", true) ->
                            "The portal returned an unexpected response. Try again or reload the portal in Settings."
                        msg.isBlank() -> "Could not start playback."
                        else -> msg
                    }
                    _uiState.update { it.copy(loading = false, switching = false, reconnecting = false, quality = quality, error = friendly) }
                }
            )
        }
    }

    /**
     * Live TV: flip to the next (+1) / previous (-1) channel. The channel NAME updates instantly
     * (no network), but the actual stream resolve is DEBOUNCED — while the user surfs quickly we
     * keep cancelling the pending resolve and only tune the channel they finally STOP on. This is
     * what makes fast surfing feel like a real STB: intermediate channels are never fetched.
     */
    fun switchChannel(delta: Int) {
        if (!isLive || liveContext.channels.isEmpty()) return
        // Long forward surf: nearing the end of the handed-off list → pull the genre's NEXT page
        // and extend it, so fullscreen surfing runs through the WHOLE genre instead of stopping
        // at however many pages the list screen had loaded (the "gets stuck far down" bug).
        if (delta > 0 && liveIndex + delta >= liveContext.channels.size - PAGE_AHEAD) extendChannelList()
        val newIndex = (liveIndex + delta).coerceIn(0, liveContext.channels.lastIndex)
        if (newIndex == liveIndex) return
        val ch = liveContext.channelAt(newIndex) ?: return
        liveIndex = newIndex
        liveContext.index = newIndex
        currentCmd = ch.cmd
        retryCount = 0          // new channel — fresh retry budget
        warmLink = null         // the warm link was for the old channel — discard it
        hasStartedPlaying = false
        // Instant UI: show the new channel immediately so surfing feels responsive. Clear any
        // leftover reconnect/loading state from the channel we just left so no pill lingers.
        _uiState.update {
            it.copy(title = ch.name, channelLabel = labelFor(newIndex),
                currentIndex = newIndex,
                channelSwitch = it.channelSwitch + 1,
                switching = !isTvDevice, reconnecting = false, loading = false, error = null)
        }
        // TV: the dual-player controller reacts to currentIndex and does its own debounced tune —
        // the old channel keeps playing until the new one is READY. Nothing else to do here.
        if (isTvDevice) return
        // Mobile (single-player path): cancel everything tied to the channel we just surfed past.
        switchJob?.cancel()
        resolveJob?.cancel()
        warmJob?.cancel()
        prefetchJob?.cancel()
        // Debounce: resolve ONLY once the user settles (coalesces a burst of presses into one tune).
        switchJob = viewModelScope.launch {
            delay(SWITCH_DEBOUNCE_MS)
            if (isActive) resolve(Tune.SWITCH)
        }
    }

    // Single-flight page extension while surfing (via the channel list VM's loader).
    private var extendJob: Job? = null

    private fun extendChannelList() {
        if (extendJob?.isActive == true) return
        val more = liveContext.loadMore ?: return
        extendJob = viewModelScope.launch {
            val updated = runCatching { more() }.getOrNull() ?: return@launch
            if (updated.size > liveContext.channels.size) liveContext.channels = updated
        }
    }

    /**
     * Link-only warm (no player, no CDN session): resolves the channel's link into the shared
     * cache (consume=false) so the NEXT tune of that channel is a zero-round-trip cache hit.
     * Used by the dual-player controller to keep the OPPOSITE surf direction warm too.
     */
    suspend fun warmLinkFor(index: Int) {
        val chCmd = liveContext.channelAt(index)?.cmd ?: return
        contentRepository.createStreamLink("itv", chCmd, null, useCache = true, consume = false)
    }

    /**
     * Keeps [warmLink] topped up with a fresh stream link for the CURRENT live channel, so a
     * stall recovery can swap instantly. Runs only for live; one keeper at a time.
     */
    private fun startWarmKeeper() {
        if (!isLive) return
        warmJob?.cancel()
        warmJob = viewModelScope.launch {
            while (isActive) {
                delay(WARM_INTERVAL_MS)
                // consume=true: held privately in warmLink for the next reconnect — must not sit
                // in the shared cache where another resolve could grab (and burn) it.
                contentRepository.createStreamLink(type, currentCmd, null, consume = true)
                    .onSuccess { if (isActive) warmLink = it }
            }
        }
    }

    /**
     * SLIDING WINDOW prefetch — the key to zero-latency surfing. Once a channel settles we warm the
     * links of the [PREFETCH_WINDOW] channels on EACH side (closest first, so the most likely next
     * press is ready soonest) into the repository's link cache. Every settle re-centres the window,
     * so it slides with the user — whichever way they surf next, that channel is already resolved.
     *
     * Deliberately a NEIGHBOURHOOD, not the whole list: Stalker links expire in ~a couple of minutes
     * and firing dozens at once gets the MAC rate-limited/blocked, so warming all 50 would leave
     * half of them dead AND risk the portal. A bounded, sliding window gives the instant feel safely.
     * Runs gently (sequential, current channel first) so it never floods the portal.
     */
    private fun prefetchAdjacent() {
        if (!isLive) return
        prefetchJob?.cancel()
        val center = liveIndex
        prefetchJob = viewModelScope.launch {
            delay(PREFETCH_DELAY_MS) // the just-tuned channel gets the network first
            for (step in 1..PREFETCH_WINDOW) {
                for (idx in intArrayOf(center + step, center - step)) {
                    // If the user has already surfed past `center`, abandon this window — a new
                    // prefetch has (or will) start around the new position.
                    if (!isActive || liveIndex != center) return@launch
                    liveContext.channelAt(idx)?.let { ch ->
                        contentRepository.createStreamLink("itv", ch.cmd, null, useCache = true)
                        // Space the warms out so at most ONE background call is on the wire at a
                        // time — a foreground channel switch is never stuck queued behind a burst.
                        delay(PREFETCH_GAP_MS)
                    }
                }
            }
        }
    }

    /**
     * Called by the player when playback fails mid-stream. Live Stalker links carry a
     * time-limited token; the cure is a fresh link + retry. We distinguish the first tune of a
     * channel (retry SILENTLY with a fresh link — no "Reconnecting…" flash) from a genuine
     * mid-stream drop (show the reconnect pill and prefer the warm link for an instant swap).
     */
    fun onPlaybackError() {
        val maxRetries = if (isLive) MAX_LIVE_RETRIES else MAX_VOD_RETRIES
        if (retryCount >= maxRetries) {
            _uiState.update {
                it.copy(
                    reconnecting = false, switching = false,
                    error = if (isLive) "Channel unavailable. Press ▲ ▼ to change channel, or OK to retry."
                    else "Playback error. Please try again."
                )
            }
            return
        }
        retryCount++
        val initialTune = !hasStartedPlaying
        viewModelScope.launch {
            // Initial-tune failure: quick, silent, fresh-link retry. Mid-stream drop: gentle backoff.
            delay(if (initialTune) 150L else 800L * retryCount)
            resolve(if (initialTune) Tune.SILENT_RETRY else Tune.RECONNECT)
        }
    }

    /** Playback is healthy again — clear the reconnect indicator and refill the retry budget. */
    fun onPlaybackResumed() {
        retryCount = 0
        hasStartedPlaying = true
        if (_uiState.value.reconnecting || _uiState.value.switching) {
            _uiState.update { it.copy(reconnecting = false, switching = false) }
        }
    }

    /**
     * Persists how far the user has watched (movies/episodes only). Skips the first 10s
     * (nothing to resume) and clears the entry near the end so a finished title starts fresh.
     */
    fun saveProgress(positionMs: Long, durationMs: Long) {
        if (!isResumable || positionMs <= 0) return
        viewModelScope.launch {
            when {
                durationMs > 0 && positionMs >= durationMs - 15_000 -> {
                    progressStore.clear(progressKey)
                    // Finished — it no longer belongs on the "Continue watching" rail.
                    continueWatching.remove(progressKey)
                }
                positionMs > 10_000 -> {
                    progressStore.save(progressKey, positionMs)
                    rememberForContinueWatching(positionMs, durationMs)
                }
            }
        }
    }

    /**
     * Mirrors the saved position into the "Continue watching" rail. The poster/name come from the
     * browse screen's hand-off ([VodPlaybackContext]); if that's missing or is about a DIFFERENT
     * title (a stale hand-off), the rail is simply left alone rather than showing the wrong card.
     */
    private suspend fun rememberForContinueWatching(positionMs: Long, durationMs: Long) {
        val item = vodContext.item?.takeIf { it.cmd == cmd } ?: return
        continueWatching.upsert(
            WatchedEntry(
                item = item,
                series = series,
                progressKey = progressKey,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override fun onCleared() {
        warmJob?.cancel()
        switchJob?.cancel()
        resolveJob?.cancel()
        prefetchJob?.cancel()
        super.onCleared()
    }

    private fun decode(value: String?): String =
        value?.let { String(Base64.decode(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)) }
            ?: ""

    private companion object {
        // Live should self-heal aggressively (token keeps refreshing); VOD only briefly.
        const val MAX_LIVE_RETRIES = 6
        const val MAX_VOD_RETRIES = 2
        // How often to pre-fetch a fresh live link so a recovery has a warm token ready.
        const val WARM_INTERVAL_MS = 90_000L
        // Wait this long after the LAST Next/Prev press before tuning — coalesces a rapid surf into
        // one resolve. Kept short (a held remote repeats faster than this) so a SINGLE forward/back
        // press tunes almost immediately instead of feeling laggy.
        const val SWITCH_DEBOUNCE_MS = 150L
        // Short delay before warming neighbours — small so the IMMEDIATE next/prev link is ready
        // fast (a single forward/back then lands on an instant cache hit), but non-zero so the
        // just-tuned channel still gets the wire first. The warm hits the portal, not the CDN the
        // current channel streams from, so it barely competes.
        const val PREFETCH_DELAY_MS = 200L
        // Gap between neighbour warms — keeps at most one background call on the wire at a time
        // (leaves the connection free for a foreground channel switch).
        const val PREFETCH_GAP_MS = 300L
        // Channels on EACH side to keep warmed (sliding window, mobile path only — TV live uses
        // the controller's direction prefetch). ±2, NOT ±5: on slow reseller portals the bigger
        // window's background create_link warms queued AHEAD of foreground requests server-side
        // and made lists/previews feel slow.
        const val PREFETCH_WINDOW = 2
        // Extend the surf list this many channels BEFORE its end so the next page is usually
        // already appended by the time the user reaches the edge.
        const val PAGE_AHEAD = 4
    }
}
