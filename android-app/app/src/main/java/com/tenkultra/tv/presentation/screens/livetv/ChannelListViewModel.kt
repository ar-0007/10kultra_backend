package com.tenkultra.tv.presentation.screens.livetv

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenkultra.tv.data.api.LivePlaybackContext
import com.tenkultra.tv.data.datastore.FavoritesStore
import com.tenkultra.tv.data.repository.AuthRepository
import com.tenkultra.tv.data.repository.ContentRepository
import com.tenkultra.tv.data.repository.SUBSCRIPTION_EXPIRED_MESSAGE
import com.tenkultra.tv.domain.model.Channel
import com.tenkultra.tv.domain.model.EpgProgram
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class ChannelListUiState(
    val genreTitle: String = "",
    val items: List<Channel> = emptyList(),
    val totalItems: Int = 0,
    val maxPageItems: Int = 14,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val epg: List<EpgProgram> = emptyList(),
    val epgLoading: Boolean = false,
    val epgForChannelId: String? = null,
    /** The user's favourite channels, in their chosen (reorderable) order. */
    val favorites: List<Channel> = emptyList(),
    /** Fast membership lookup so a row can show its ★ without scanning the list. */
    val favoriteIds: Set<String> = emptySet()
) {
    val totalPages: Int
        get() = if (maxPageItems <= 0) 1 else ((totalItems + maxPageItems - 1) / maxPageItems).coerceAtLeast(1)
}

@HiltViewModel
class ChannelListViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val authRepository: AuthRepository,
    private val liveContext: LivePlaybackContext,
    private val favoritesStore: FavoritesStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val genreId: String = savedStateHandle["genreId"] ?: "*"
    private val genreTitle: String =
        URLDecoder.decode(savedStateHandle["genreTitle"] ?: "", "UTF-8")

    private val _uiState = MutableStateFlow(ChannelListUiState(genreTitle = genreTitle))
    val uiState = _uiState.asStateFlow()

    private var nextPage = 1
    private var hasMore = true
    private var loadingFlag = false
    private var emptyStreak = 0 // consecutive pages that added nothing new (portal repeating a page)
    private var previewPrefetchJob: Job? = null

    /** A channel's most-unique key. The list `id` repeats across entries on some portals — deduping
     *  by it wrongly dropped valid channels AND froze paging mid-list — so prefer the play `cmd`. */
    private fun dedupKey(c: Channel): String =
        c.cmd.takeIf { it.isNotBlank() } ?: "${c.id}|${c.number}|${c.name}"

    init {
        loadNextPage()
        // Keep favourites (and their order) live from disk so ★ marks and the FAVORITES
        // filter update the instant the user toggles or reorders one.
        viewModelScope.launch {
            favoritesStore.favoritesFlow.collect { favs ->
                _uiState.update { it.copy(favorites = favs, favoriteIds = favs.mapTo(HashSet()) { c -> c.id }) }
            }
        }
    }

    /** Yellow button: add/remove the highlighted channel from favourites. */
    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch { favoritesStore.toggle(channel) }
    }

    /** Blue button (Move mode): shift a favourite up (-1) / down (+1) to reorder it. */
    fun moveFavorite(index: Int, delta: Int) {
        viewModelScope.launch { favoritesStore.move(index, delta) }
    }

    /**
     * Resolves a playable stream URL for the side PREVIEW (muted). Same create_link the full
     * player uses, so the preview shows the real channel. Returns null on any failure — the
     * preview simply keeps showing the logo/placeholder rather than erroring.
     */
    suspend fun resolvePreviewUrl(channel: Channel, fresh: Boolean = false): String? =
        // useCache=true: reuse the warmed link for an instant tune-in. consume=true: this link is
        // about to be PLAYED, so it's removed from the cache — re-previewing the same channel later
        // then gets a FRESH link instead of the consumed (dead) one that 403'd the second view.
        // `fresh=true` (preview retry) forces a new link outright.
        contentRepository.createStreamLink("itv", channel.cmd, null, useCache = !fresh, consume = true)
            .getOrNull()

    /**
     * SLIDING WINDOW preview prefetch — warms the stream links of the [PREVIEW_WINDOW] channels on
     * each side of the current selection into the link cache, so moving the preview to a neighbour
     * (or pressing OK to open one fullscreen) is an instant cache hit. Same idea as the full player:
     * a bounded, sliding NEIGHBOURHOOD — never the whole list — so short-lived portal tokens and
     * rate-limits are never a problem. Debounced + cancellable so fast scrolling can't flood the portal.
     */
    fun prefetchPreviewWindow(channels: List<Channel>, centerIndex: Int) {
        previewPrefetchJob?.cancel()
        previewPrefetchJob = viewModelScope.launch {
            delay(PREVIEW_PREFETCH_DEBOUNCE_MS) // only warm once the highlight settles
            // step 0 = the HIGHLIGHTED channel itself, warmed FIRST so pressing OK previews it
            // instantly (cache hit); then the neighbours for an instant preview switch.
            for (step in 0..PREVIEW_WINDOW) {
                val idxs = if (step == 0) intArrayOf(centerIndex)
                else intArrayOf(centerIndex + step, centerIndex - step)
                for (idx in idxs) {
                    if (!isActive) return@launch
                    channels.getOrNull(idx)?.let { ch ->
                        contentRepository.createStreamLink("itv", ch.cmd, null, useCache = true)
                        delay(PREVIEW_PREFETCH_GAP_MS) // one background warm on the wire at a time
                    }
                }
            }
        }
    }

    fun loadNextPage() {
        viewModelScope.launch { doLoadNextPage() }
    }

    private suspend fun doLoadNextPage() {
        if (!hasMore || loadingFlag) return
        loadingFlag = true
        val first = _uiState.value.items.isEmpty()
        _uiState.update { it.copy(loading = first, loadingMore = !first) }
        run {
            contentRepository.getChannels(genreId, nextPage).fold(
                onSuccess = { page ->
                    val seen = _uiState.value.items.mapTo(HashSet()) { dedupKey(it) }
                    val fresh = page.items.filter { seen.add(dedupKey(it)) }
                    val combined = _uiState.value.items + fresh
                    // Keep paging to the END of the list. Stop ONLY when: the portal returns a
                    // genuinely empty page (no more data), OR we've collected the known total, OR
                    // several pages in a row add nothing new (portal repeating its last page for
                    // out-of-range page numbers). A SINGLE duplicate page no longer freezes paging
                    // mid-list — that was the "can't scroll to the end, only goes back" bug.
                    if (fresh.isEmpty()) emptyStreak++ else emptyStreak = 0
                    val reachedTotal = page.totalItems > 0 && combined.size >= page.totalItems
                    hasMore = page.items.isNotEmpty() && !reachedTotal && emptyStreak < MAX_EMPTY_STREAK
                    nextPage += 1
                    loadingFlag = false
                    val errorMsg = if (combined.isEmpty()) emptyOrExpiredMessage("No channels found.") else null
                    _uiState.update {
                        it.copy(
                            items = combined,
                            totalItems = page.totalItems,
                            maxPageItems = page.maxPageItems,
                            loading = false,
                            loadingMore = false,
                            error = errorMsg
                        )
                    }
                },
                onFailure = {
                    loadingFlag = false
                    val errorMsg =
                        if (_uiState.value.items.isEmpty()) emptyOrExpiredMessage("Couldn't load channels.") else null
                    _uiState.update {
                        it.copy(loading = false, loadingMore = false, error = errorMsg)
                    }
                }
            )
        }
    }

    /** When there's nothing to show, distinguish an expired subscription from an ordinary load error. */
    private suspend fun emptyOrExpiredMessage(fallback: String): String =
        if (authRepository.subscriptionExpired()) SUBSCRIPTION_EXPIRED_MESSAGE else fallback

    fun onSelectionChanged(index: Int) {
        // Prefetch the NEXT page a full page before the edge (not the whole list at once —
        // that hammered the portal and corrupted the channel list). One gentle on-demand
        // request keeps scrolling ahead of the network without overloading the server.
        val ahead = _uiState.value.maxPageItems.coerceIn(6, 20)
        if (index >= _uiState.value.items.size - ahead) loadNextPage()
    }

    /** Set true when we hand off to the full player, so on return we can restore the highlight to
     *  whatever channel the user surfed to IN the player (not the one they entered on). */
    private var handedToPlayer = false

    /**
     * Hands the (possibly filtered) channel list + index to the player for in-player flipping.
     * [canPage] = the hand-off is the FULL unfiltered genre list, so the player may pull further
     * pages through us while the user long-surfs forward — fullscreen then reaches the WHOLE
     * genre, and the list screen shows the same extra pages on return.
     */
    fun prepareLivePlayback(channels: List<Channel>, index: Int, canPage: Boolean = false) {
        val loadMore: (suspend () -> List<Channel>)? = if (canPage) {
            { doLoadNextPage(); _uiState.value.items }
        } else null
        liveContext.set(channels, index, loadMore)
        handedToPlayer = true
    }

    /**
     * On returning from the full player, gives the index of the channel the user was LAST watching
     * (they may have surfed with ▲▼ inside the player). The list screen moves its highlight there so
     * the placeholder lands on the channel you backed out of. Returns null on a normal (non-return)
     * entry so a fresh list open isn't hijacked by a stale playback index.
     */
    fun consumeReturnIndex(): Int? =
        if (handedToPlayer) { handedToPlayer = false; liveContext.index } else null

    /** Loads the short EPG (TV-guide schedule) for a channel, when the guide panel is open. */
    fun loadEpg(channel: Channel) {
        if (_uiState.value.epgForChannelId == channel.id && _uiState.value.epg.isNotEmpty()) return
        _uiState.update { it.copy(epgLoading = true, epg = emptyList(), epgForChannelId = channel.id) }
        viewModelScope.launch {
            val list = contentRepository.getShortEpg(channel.id).getOrDefault(emptyList())
            // Ignore a stale result if the selection moved on.
            if (_uiState.value.epgForChannelId == channel.id) {
                _uiState.update { it.copy(epg = list, epgLoading = false) }
            }
        }
    }

    private companion object {
        // Channels on EACH side of the highlighted one to keep warmed (plus the highlighted one).
        // ±1 ONLY: the live portal's UNPLAYED tokens die in <10s, so a wide window mostly minted
        // tokens that were dead by the time they were used (the "no preview / second time black"
        // bug) AND queued ahead of foreground requests on slow reseller portals. Highlighted
        // channel + immediate neighbours is the sweet spot: always fresh, near-zero waste.
        const val PREVIEW_WINDOW = 1
        // Wait for the highlight to settle before warming — short so the highlighted channel's link
        // is ready FAST (OK then = instant cache hit), yet enough to skip channels flown past.
        const val PREVIEW_PREFETCH_DEBOUNCE_MS = 120L
        // Gap between warms so at most one background call is on the wire at a time (leaves the
        // connection free for a foreground tune).
        const val PREVIEW_PREFETCH_GAP_MS = 300L
        // Stop paging after this many consecutive pages add nothing new (portal repeating a page).
        const val MAX_EMPTY_STREAK = 3
    }
}
