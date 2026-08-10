package com.tenkultra.tv.presentation.screens.modern

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenkultra.tv.data.api.LivePlaybackContext
import com.tenkultra.tv.data.api.VodPlaybackContext
import com.tenkultra.tv.data.parental.ParentalControl
import com.tenkultra.tv.data.repository.ContentRepository
import com.tenkultra.tv.domain.model.Category
import com.tenkultra.tv.domain.model.Channel
import com.tenkultra.tv.domain.model.VodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ModernSection { LIVE_TV, VOD }

data class ModernHomeUiState(
    val tvGenres: List<Category> = emptyList(),
    val vodCategories: List<Category> = emptyList(),
    val section: ModernSection = ModernSection.VOD,
    val vodItems: List<VodItem> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val loadingCategories: Boolean = true,
    val loadingContent: Boolean = false,
    val error: String? = null
) {
    fun categories(): List<Category> =
        if (section == ModernSection.VOD) vodCategories else tvGenres
}

@HiltViewModel
class ModernHomeViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val liveContext: LivePlaybackContext,
    private val vodContext: VodPlaybackContext,
    private val parental: ParentalControl
) : ViewModel() {

    /**
     * Hands the live-TV channel list + the picked index to the player so in-player
     * next/previous flips follow the SAME order as the on-screen list (matches Classic).
     */
    fun prepareLivePlayback(channels: List<Channel>, index: Int) {
        liveContext.set(channels, index)
    }

    /** Hands the opened title to the player so it can build its "Continue watching" card. */
    fun prepareVodPlayback(item: VodItem) = vodContext.set(item)

    private val _uiState = MutableStateFlow(ModernHomeUiState())
    val uiState = _uiState.asStateFlow()

    private var currentCategoryId: String? = null
    private var page = 1
    private var hasMore = true
    private var loadingFlag = false

    init {
        viewModelScope.launch {
            val genres = contentRepository.getTvGenres().getOrDefault(emptyList())
            val cats = contentRepository.getVodCategories().getOrDefault(emptyList())
            _uiState.update { it.copy(tvGenres = genres, vodCategories = cats, loadingCategories = false) }
            // default: VOD, first category — CHILD LOCK: an adult first category stays closed
            // until the PIN is entered from the list.
            cats.firstOrNull()?.takeUnless { parental.isLocked(it.title) }?.let { selectCategory(it) }
        }
    }

    fun setSection(section: ModernSection) {
        if (_uiState.value.section == section) return
        _uiState.update { it.copy(section = section, vodItems = emptyList(), channels = emptyList()) }
        // CHILD LOCK — same rule when switching Live TV / VOD: never auto-open an adult category.
        viewModelScope.launch {
            _uiState.value.categories().firstOrNull()?.takeUnless { parental.isLocked(it.title) }
                ?.let { selectCategory(it) }
        }
    }

    /** CHILD LOCK — empties the grid/list when the cursor lands on a locked category. */
    fun clearContent() {
        currentCategoryId = null
        loadingFlag = false
        _uiState.update { it.copy(vodItems = emptyList(), channels = emptyList(), loadingContent = false) }
    }

    fun selectCategory(category: Category) {
        currentCategoryId = category.id
        page = 1
        hasMore = true
        loadingFlag = false
        _uiState.update { it.copy(vodItems = emptyList(), channels = emptyList(), loadingContent = true) }
        loadContent(category.id, reset = true)
    }

    fun loadMore() {
        val id = currentCategoryId ?: return
        if (!hasMore || loadingFlag) return
        loadContent(id, reset = false)
    }

    private fun loadContent(categoryId: String, reset: Boolean) {
        if (loadingFlag) return
        loadingFlag = true
        val section = _uiState.value.section
        viewModelScope.launch {
            _uiState.update { it.copy(loadingContent = true) }
            if (section == ModernSection.VOD) {
                contentRepository.getVodList(categoryId, page).fold(
                    onSuccess = { p ->
                        loadingFlag = false
                        if (categoryId != currentCategoryId) return@fold
                        val existing = if (reset) emptyList() else _uiState.value.vodItems
                        val seen = existing.mapTo(HashSet()) { it.id }
                        val combined = existing + p.items.filter { seen.add(it.id) }
                        hasMore = combined.size < p.totalItems && p.items.isNotEmpty()
                        page += 1
                        _uiState.update { it.copy(vodItems = combined, loadingContent = false, error = null) }
                    },
                    onFailure = { loadingFlag = false; _uiState.update { it.copy(loadingContent = false) } }
                )
            } else {
                contentRepository.getChannels(categoryId, page).fold(
                    onSuccess = { p ->
                        loadingFlag = false
                        if (categoryId != currentCategoryId) return@fold
                        val existing = if (reset) emptyList() else _uiState.value.channels
                        val seen = existing.mapTo(HashSet()) { it.id }
                        val combined = existing + p.items.filter { seen.add(it.id) }
                        hasMore = combined.size < p.totalItems && p.items.isNotEmpty()
                        page += 1
                        _uiState.update { it.copy(channels = combined, loadingContent = false, error = null) }
                    },
                    onFailure = { loadingFlag = false; _uiState.update { it.copy(loadingContent = false) } }
                )
            }
        }
    }
}
