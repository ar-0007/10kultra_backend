package com.tenkultra.tv.presentation.screens.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenkultra.tv.data.api.VodPlaybackContext
import com.tenkultra.tv.data.datastore.VodFavoritesStore
import com.tenkultra.tv.data.repository.ContentRepository
import com.tenkultra.tv.domain.model.VodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class VodListUiState(
    val categoryId: String = "",
    val categoryTitle: String = "",
    val items: List<VodItem> = emptyList(),
    val totalItems: Int = 0,
    val maxPageItems: Int = 14,
    val query: String = "",
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    /** The user's favourite movies/series, in their chosen (reorderable) order. */
    val favorites: List<VodItem> = emptyList(),
    /** Fast membership lookup so a row can show its ★ without scanning the list. */
    val favoriteIds: Set<String> = emptySet()
) {
    val totalPages: Int
        get() = if (maxPageItems <= 0) 1 else ((totalItems + maxPageItems - 1) / maxPageItems).coerceAtLeast(1)
}

@HiltViewModel
class VodListViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val favoritesStore: VodFavoritesStore,
    private val vodContext: VodPlaybackContext,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val categoryId: String = savedStateHandle["categoryId"] ?: "*"
    private val categoryTitle: String =
        URLDecoder.decode(savedStateHandle["categoryTitle"] ?: "", "UTF-8")

    /** Hands the opened title to the player so it can build its "Continue watching" card. */
    fun prepareVodPlayback(item: VodItem, series: Int = 0) = vodContext.set(item, series)

    private val _uiState =
        MutableStateFlow(VodListUiState(categoryId = categoryId, categoryTitle = categoryTitle))
    val uiState = _uiState.asStateFlow()

    private var nextPage = 1
    private var hasMore = true
    private var loadingFlag = false // synchronous guard against the fast-scroll race
    private var query = ""          // active search term ("" = browse the category)
    private var searchJob: Job? = null
    private var detailPrefetchJob: Job? = null // warms a highlighted series' seasons/episodes

    init {
        loadNextPage()
        // Keep favourites (and their order) live from disk so ★ marks and the FAVORITES
        // filter update the instant the user toggles or reorders one.
        viewModelScope.launch {
            favoritesStore.favoritesFlow.collect { favs ->
                _uiState.update { it.copy(favorites = favs, favoriteIds = favs.mapTo(HashSet()) { v -> v.id }) }
            }
        }
    }

    /** Yellow button: add/remove the highlighted movie/series from favourites. */
    fun toggleFavorite(item: VodItem) {
        viewModelScope.launch { favoritesStore.toggle(item) }
    }

    /** Blue button (Move mode): shift a favourite up (-1) / down (+1) to reorder it. */
    fun moveFavorite(index: Int, delta: Int) {
        viewModelScope.launch { favoritesStore.move(index, delta) }
    }

    /** Debounced title search. Blank query falls back to browsing the category. */
    fun setQuery(raw: String) {
        _uiState.update { it.copy(query = raw) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            query = raw.trim()
            nextPage = 1
            hasMore = true
            loadingFlag = false
            _uiState.update { it.copy(items = emptyList(), loading = true, error = null) }
            loadNextPage()
        }
    }

    /** Loads the next page and appends it; safe to call repeatedly while scrolling. */
    fun loadNextPage() {
        if (!hasMore || loadingFlag) return
        loadingFlag = true
        val first = _uiState.value.items.isEmpty()
        _uiState.update { it.copy(loading = first, loadingMore = !first) }
        viewModelScope.launch {
            val source = if (query.isBlank()) contentRepository.getVodList(categoryId, nextPage)
            else contentRepository.searchVod(query, nextPage)
            source.fold(
                onSuccess = { page ->
                    // Dedupe by id so overlapping pages don't show repeating rows.
                    val seen = _uiState.value.items.mapTo(HashSet()) { it.id }
                    val fresh = page.items.filter { seen.add(it.id) }
                    val combined = _uiState.value.items + fresh
                    hasMore = combined.size < page.totalItems && page.items.isNotEmpty()
                    nextPage += 1
                    loadingFlag = false
                    _uiState.update {
                        it.copy(
                            items = combined,
                            totalItems = page.totalItems,
                            maxPageItems = page.maxPageItems,
                            loading = false,
                            loadingMore = false,
                            error = if (combined.isEmpty()) "No items found." else null
                        )
                    }
                    // Warm the NEXT page into the cache now, so when the user scrolls to it the
                    // append is an instant cache hit instead of a fresh portal round-trip.
                    if (hasMore) prefetchNextPage(nextPage)
                },
                onFailure = {
                    loadingFlag = false
                    // Don't surface raw parse errors mid-scroll; just stop paging.
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            error = if (it.items.isEmpty()) "Couldn't load this category." else null
                        )
                    }
                }
            )
        }
    }

    /** Trigger paging when the selection nears the end of the loaded items. */
    fun onSelectionChanged(index: Int) {
        if (index >= _uiState.value.items.size - 3) loadNextPage()
        prefetchDetailIfSeries(index)
    }

    /**
     * PREDICTIVE PREFETCH for series: when a SERIES row is highlighted, warm its seasons AND the
     * first season's episodes in the background (both land in [ContentRepository]'s caches). By the
     * time the user presses OK, the detail screen paints its episode list instantly — no waiting on
     * the two sequential portal calls (get_seasons → get_episodes) that made series open slowly.
     * Debounced so scrolling through rows only fires once the highlight settles on a series.
     */
    private fun prefetchDetailIfSeries(index: Int) {
        val item = _uiState.value.items.getOrNull(index)?.takeIf { it.isSeries } ?: return
        detailPrefetchJob?.cancel()
        detailPrefetchJob = viewModelScope.launch {
            delay(300)
            val seasons = contentRepository.getSeasons(item.id, categoryId).getOrNull().orEmpty()
            seasons.minByOrNull { it.number }?.let { s ->
                contentRepository.getEpisodes(item.id, s.id, s.cmd.ifBlank { item.cmd })
            }
        }
    }

    /** Fire-and-forget warm of a page into the repository cache (ignored result). */
    private fun prefetchNextPage(page: Int) {
        if (query.isNotBlank()) return // only browse pages are cached; search is query-specific
        viewModelScope.launch { contentRepository.getVodList(categoryId, page) }
    }
}
