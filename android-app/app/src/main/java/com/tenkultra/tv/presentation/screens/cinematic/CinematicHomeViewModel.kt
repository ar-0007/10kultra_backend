package com.tenkultra.tv.presentation.screens.cinematic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenkultra.tv.data.api.LivePlaybackContext
import com.tenkultra.tv.data.api.VodPlaybackContext
import com.tenkultra.tv.data.datastore.ContinueWatchingStore
import com.tenkultra.tv.data.datastore.VodFavoritesStore
import com.tenkultra.tv.data.repository.ContentRepository
import com.tenkultra.tv.domain.model.Channel
import com.tenkultra.tv.domain.model.VodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** What a rail is, so the screen knows how to draw it and what OK should do on its items. */
enum class RailKind {
    CATEGORY,  // a Live TV genre or a VOD category from the portal
    CONTINUE,  // half-watched titles — OK resumes them straight away
    MY_LIST    // the user's favourite movies / series
}

/**
 * One horizontal rail in the Cinematic layout — a Live TV genre (channels) OR a VOD category
 * (movies/series). Items load LAZILY the first time the rail is shown, so the screen can list
 * EVERY genre + category the portal sends without fetching them all upfront.
 *
 * The two personal rails ([RailKind.CONTINUE] and [RailKind.MY_LIST]) are built from local storage
 * and are therefore always [loaded].
 */
data class CinematicRail(
    val id: String,
    val title: String,
    val isLive: Boolean,
    val channels: List<Channel> = emptyList(),
    val vod: List<VodItem> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val kind: RailKind = RailKind.CATEGORY,
    /** Continue-watching only: 0f..1f watched fraction, keyed by the item's cmd. */
    val progressByCmd: Map<String, Float> = emptyMap(),
    /** Continue-watching only: the episode number to resume, keyed by the item's cmd (0 = movie). */
    val seriesByCmd: Map<String, Int> = emptyMap()
) {
    val size: Int get() = if (isLive) channels.size else vod.size
}

data class CinematicUiState(
    val loading: Boolean = true,
    val rails: List<CinematicRail> = emptyList(),
    // Search (VOD only — movies + series).
    val searchQuery: String = "",
    val searchResults: List<VodItem> = emptyList(),
    val searching: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CinematicHomeViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val liveContext: LivePlaybackContext,
    private val vodContext: VodPlaybackContext,
    private val continueWatching: ContinueWatchingStore,
    private val vodFavorites: VodFavoritesStore
) : ViewModel() {

    private val _ui = MutableStateFlow(CinematicUiState())
    val ui = _ui.asStateFlow()

    private var searchJob: Job? = null
    private val inFlight = ConcurrentHashMap.newKeySet<String>() // rail ids currently loading

    /** The portal's own rails, kept separate so the personal rails can be re-prepended on change. */
    private var portalRails: List<CinematicRail> = emptyList()

    /** "Continue watching" + "My list" — rebuilt whenever their local stores change. */
    private var personalRails: List<CinematicRail> = emptyList()

    init {
        load()
        watchPersonalRails()
    }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // ALL Live TV genres first (each = its own rail), then ALL VOD categories.
            val genres = contentRepository.getTvGenres().getOrDefault(emptyList())
                .filterNot { it.title.equals("All", true) }
            val cats = contentRepository.getVodCategories().getOrDefault(emptyList())
                .filterNot { it.title.equals("All", true) }
            portalRails = genres.map { CinematicRail(it.id, it.title, isLive = true) } +
                cats.map { CinematicRail(it.id, it.title, isLive = false) }
            if (portalRails.isEmpty()) {
                _ui.update { it.copy(loading = false, error = "No content available.") }
                return@launch
            }
            _ui.update { it.copy(loading = false, rails = withPersonalRails(portalRails)) }
            // Warm the first few PORTAL rails so the screen isn't empty on arrival.
            val start = _ui.value.rails.size - portalRails.size
            for (i in start until minOf(start + 4, _ui.value.rails.size)) ensureRail(i)
        }
    }

    /**
     * Keeps "Continue watching" + "My list" live: both are local stores, so the rails re-appear (or
     * vanish) the moment the user finishes a title or toggles a favourite — no reload needed.
     */
    private fun watchPersonalRails() {
        viewModelScope.launch {
            combine(continueWatching.entriesFlow, vodFavorites.favoritesFlow) { watched, favorites ->
                buildList {
                    if (watched.isNotEmpty()) {
                        add(
                            CinematicRail(
                                id = RAIL_CONTINUE,
                                title = "Continue watching",
                                isLive = false,
                                vod = watched.map { it.item },
                                loaded = true,
                                kind = RailKind.CONTINUE,
                                progressByCmd = watched.associate { it.item.cmd to it.fraction },
                                seriesByCmd = watched.associate { it.item.cmd to it.series }
                            )
                        )
                    }
                    if (favorites.isNotEmpty()) {
                        add(
                            CinematicRail(
                                id = RAIL_MY_LIST,
                                title = "My list",
                                isLive = false,
                                vod = favorites,
                                loaded = true,
                                kind = RailKind.MY_LIST
                            )
                        )
                    }
                }
            }.collect { personal ->
                personalRails = personal
                if (portalRails.isNotEmpty() || personal.isNotEmpty()) {
                    _ui.update { it.copy(rails = withPersonalRails(portalRails)) }
                }
            }
        }
    }

    /** Personal rails always sit at the very top, above the portal's own categories. */
    private fun withPersonalRails(portal: List<CinematicRail>): List<CinematicRail> {
        // Preserve whatever the portal rails have already loaded.
        val loadedById = _ui.value.rails.filter { it.kind == RailKind.CATEGORY }
            .associateBy { "${it.isLive}:${it.id}" }
        val merged = portal.map { loadedById["${it.isLive}:${it.id}"] ?: it }
        return personalRails + merged
    }

    /**
     * Lazily loads a rail's first page the first time it's shown (idempotent + race-safe).
     * Takes the rail itself so a caller showing a FILTERED or RE-ORDERED list (the phone puts VOD
     * first) can't accidentally warm the wrong one.
     */
    fun ensureRail(rail: CinematicRail) = ensureRailInternal(rail)

    /** Index-based overload for callers that render [CinematicUiState.rails] in its own order. */
    fun ensureRail(index: Int) {
        ensureRailInternal(_ui.value.rails.getOrNull(index) ?: return)
    }

    private fun ensureRailInternal(rail: CinematicRail) {
        if (rail.kind != RailKind.CATEGORY) return // personal rails come from local storage
        // Composite key: a Live genre and a VOD category can share the same numeric id on Stalker,
        // so keying in-flight by id alone would wrongly block one when the other is loading.
        val key = "${rail.isLive}:${rail.id}"
        if (rail.loaded || rail.loading || !inFlight.add(key)) return
        // Mark it loading by IDENTITY, not by the caller's index — the caller may be showing a
        // re-ordered list, and an index from that would flag the wrong rail.
        setRail(rail) { it.copy(loading = true) }
        viewModelScope.launch {
            val updated = if (rail.isLive) {
                val ch = contentRepository.getChannels(rail.id, 1).getOrNull()?.items.orEmpty().take(30)
                rail.copy(channels = ch, loaded = true, loading = false)
            } else {
                val v = contentRepository.getVodList(rail.id, 1).getOrNull()?.items.orEmpty().take(30)
                rail.copy(vod = v, loaded = true, loading = false)
            }
            // Re-find by id (the list may have re-emitted) and replace.
            _ui.update { st ->
                val i = st.rails.indexOfFirst {
                    it.kind == RailKind.CATEGORY && it.id == rail.id && it.isLive == rail.isLive
                }
                if (i < 0) st else st.copy(rails = st.rails.toMutableList().also { it[i] = updated })
            }
            inFlight.remove(key)
        }
    }

    private fun setRail(rail: CinematicRail, transform: (CinematicRail) -> CinematicRail) {
        _ui.update { st ->
            val i = st.rails.indexOfFirst {
                it.kind == rail.kind && it.id == rail.id && it.isLive == rail.isLive
            }
            if (i < 0) return@update st
            st.copy(rails = st.rails.toMutableList().also { it[i] = transform(it[i]) })
        }
    }

    /**
     * Hands a live rail's channel list + picked index to the player so next/prev matches.
     * Takes the rail itself, not an index — the screen shows a FILTERED list of rails, so an index
     * into it would point at the wrong rail once the personal rails shift everything down.
     */
    fun prepareLivePlayback(rail: CinematicRail, itemIndex: Int) {
        if (rail.isLive) liveContext.set(rail.channels, itemIndex)
    }

    /** Hands the opened movie/episode to the player so it can build its "Continue watching" card. */
    fun prepareVodPlayback(item: VodItem, series: Int = 0) {
        vodContext.set(item, series)
    }

    /** Drops a finished/unwanted title from the Continue-watching rail (long-press / Back menu). */
    fun removeFromContinue(item: VodItem) {
        viewModelScope.launch {
            continueWatching.entries()
                .filter { it.item.cmd == item.cmd }
                .forEach { continueWatching.remove(it.progressKey) }
        }
    }

    /** VOD search (movies + series). Debounced; blank clears results. */
    fun onSearchQuery(query: String) {
        _ui.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _ui.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _ui.update { it.copy(searching = true) }
            val res = contentRepository.searchVod(query.trim(), 1).getOrNull()?.items.orEmpty()
            _ui.update { it.copy(searchResults = res, searching = false) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _ui.update { it.copy(searchQuery = "", searchResults = emptyList(), searching = false) }
    }

    companion object {
        const val RAIL_CONTINUE = "__continue"
        const val RAIL_MY_LIST = "__my_list"
    }
}
