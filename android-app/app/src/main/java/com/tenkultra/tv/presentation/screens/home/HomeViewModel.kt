package com.tenkultra.tv.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenkultra.tv.data.repository.AuthRepository
import com.tenkultra.tv.data.repository.ContentRepository
import com.tenkultra.tv.data.repository.SUBSCRIPTION_EXPIRED_MESSAGE
import com.tenkultra.tv.domain.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Bottom carousel tabs, matching the Classic STBEMU home. */
enum class HomeTab(val label: String) {
    MEDIA_BROWSER("MEDIA BROWSER"),
    TV("TV"),
    VIDEO_CLUB("VIDEO CLUB"),
    SETTINGS("SETTINGS")
}

data class HomeUiState(
    val tvGenres: List<Category> = emptyList(),
    val vodCategories: List<Category> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    // Adult categories are gated by the app-wide CHILD LOCK (ParentalControl / ParentalGate), which
    // wraps every layout — this VM no longer owns any PIN logic of its own.

    private companion object {
        // Wait this long after a category is highlighted before prefetching it — long enough that
        // scrolling past a category doesn't trigger a fetch, short enough to beat the OK press.
        const val PREFETCH_SETTLE_MS = 280L
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(error = null) }
        // 1) Instant: show cached genres/categories so a reopen feels immediate.
        viewModelScope.launch {
            val cg = contentRepository.cachedTvGenres()
            val cc = contentRepository.cachedVodCategories()
            _uiState.update {
                it.copy(
                    tvGenres = if (it.tvGenres.isEmpty()) cg else it.tvGenres,
                    vodCategories = if (it.vodCategories.isEmpty()) cc else it.vodCategories,
                    loading = it.tvGenres.isEmpty() && cg.isEmpty() && it.vodCategories.isEmpty() && cc.isEmpty()
                )
            }
        }
        // 2) Refresh from network in the background; never wipe a non-empty list on failure.
        viewModelScope.launch {
            val genres = contentRepository.getTvGenres().getOrDefault(emptyList())
            if (genres.isNotEmpty()) _uiState.update { it.copy(tvGenres = genres, loading = false) }
        }
        viewModelScope.launch {
            val cats = contentRepository.getVodCategories().getOrDefault(emptyList())
            val nothing = _uiState.value.tvGenres.isEmpty() && _uiState.value.vodCategories.isEmpty() && cats.isEmpty()
            // Nothing loaded → is it an expired subscription or just a connection problem?
            val errorMsg = if (nothing) {
                if (authRepository.subscriptionExpired()) SUBSCRIPTION_EXPIRED_MESSAGE
                else "Could not load categories. Check the connection."
            } else null
            _uiState.update {
                it.copy(
                    vodCategories = if (cats.isNotEmpty()) cats else it.vodCategories,
                    loading = false,
                    error = errorMsg
                )
            }
        }
    }

    private var prefetchJob: Job? = null

    /**
     * PREDICTIVE PREFETCH — the key to an instant FIRST open. TV navigation is deliberate: the
     * user highlights a category, reads it, then presses OK. We use that pause to fetch the
     * category's first page in the background so it lands in [ContentRepository]'s page cache; by
     * the time OK is pressed, the list screen opens on a cache HIT (no visible portal round-trip).
     * Debounced so scrolling THROUGH categories doesn't fire a request for every one it passes.
     */
    fun prefetchCategory(category: Category, isTv: Boolean) {
        if (category.id.isBlank()) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            delay(PREFETCH_SETTLE_MS) // only fire once the highlight settles on this category
            if (isTv) contentRepository.getChannels(category.id, 1)
            else contentRepository.getVodList(category.id, 1)
        }
    }

    /** The category list shown for the currently selected tab. */
    fun categoriesFor(tab: HomeTab): List<Category> = when (tab) {
        HomeTab.TV -> _uiState.value.tvGenres
        HomeTab.VIDEO_CLUB -> _uiState.value.vodCategories
        else -> emptyList()
    }
}
