package com.tenkultra.tv.presentation.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenkultra.tv.data.api.LivePlaybackContext
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

/** The mobile bottom-nav sections: Live TV channels, and VOD (movies + series together). */
enum class MobileSection { LIVE, VOD }

data class MobileHomeUiState(
    val section: MobileSection = MobileSection.LIVE,
    val categories: List<Category> = emptyList(),      // genres for LIVE, VOD categories for MOVIES/SERIES
    val selectedCategoryId: String? = null,
    /** The title of the selected category (shown as header when drilling into a category). */
    val selectedCategoryTitle: String? = null,
    /** True when the user has picked a category and is viewing its channels/content. */
    val drillDown: Boolean = false,
    val channels: List<Channel> = emptyList(),
    val vod: List<VodItem> = emptyList(),
    val loadingCategories: Boolean = true,
    val loadingContent: Boolean = false,
    /** Persisted tab index so returning from Settings/Player keeps the same tab. */
    val tabIndex: Int = 0
)

/**
 * Mobile home data — REUSES [ContentRepository] (same as the TV UI). Loads Live TV genres + VOD
 * categories once; selecting a section/category loads its content with paging (load-on-scroll).
 */
@HiltViewModel
class MobileHomeViewModel @Inject constructor(
    private val repo: ContentRepository,
    private val liveContext: LivePlaybackContext
) : ViewModel() {

    private val _ui = MutableStateFlow(MobileHomeUiState())
    val ui = _ui.asStateFlow()

    private var tvGenres = emptyList<Category>()
    private var vodCats = emptyList<Category>()
    private var page = 1
    private var hasMore = true
    private var loading = false
    private var curCat: String? = null

    init {
        viewModelScope.launch {
            tvGenres = repo.getTvGenres().getOrDefault(emptyList()).filterNot { it.title.equals("All", true) }
            vodCats = repo.getVodCategories().getOrDefault(emptyList()).filterNot { it.title.equals("All", true) }
            applySection(MobileSection.LIVE)
        }
    }

    /**
     * Switch bottom-nav tab and persist the index so it survives navigation round-trips.
     * Tab 0 is the Netflix "Home" surface, which has its own ViewModel — it needs no section here.
     */
    fun selectTab(index: Int) {
        _ui.update { it.copy(tabIndex = index) }
        val section = when (index) {
            1 -> MobileSection.LIVE
            2 -> MobileSection.VOD
            else -> return
        }
        if (_ui.value.section != section) applySection(section)
    }

    fun selectSection(s: MobileSection) {
        if (_ui.value.section == s) return
        applySection(s)
    }

    private fun applySection(s: MobileSection) {
        val cats = if (s == MobileSection.LIVE) tvGenres else vodCats
        _ui.update {
            it.copy(
                section = s,
                categories = cats,
                loadingCategories = false,
                channels = emptyList(),
                vod = emptyList(),
                drillDown = false,
                selectedCategoryId = null,
                selectedCategoryTitle = null
            )
        }
        // Both sections show the category list first — user drills into one by tapping.
    }

    /** Select a category and load its content. Sets drillDown = true so the UI shows channels. */
    fun selectCategory(id: String, title: String? = null) {
        curCat = id; page = 1; hasMore = true; loading = false
        _ui.update {
            it.copy(
                selectedCategoryId = id,
                selectedCategoryTitle = title ?: it.selectedCategoryTitle,
                channels = emptyList(),
                vod = emptyList(),
                loadingContent = true,
                drillDown = true
            )
        }
        loadContent(reset = true)
    }

    /** Go back from drilled-down content to the category list (both Live TV and VOD). */
    fun backToCategories() {
        curCat = null; page = 1; hasMore = true; loading = false
        _ui.update {
            it.copy(
                drillDown = false,
                selectedCategoryId = null,
                selectedCategoryTitle = null,
                channels = emptyList(),
                vod = emptyList(),
                loadingContent = false
            )
        }
    }

    fun loadMore() {
        if (hasMore && !loading) loadContent(reset = false)
    }

    private fun loadContent(reset: Boolean) {
        val id = curCat ?: return
        if (loading) return
        loading = true
        val section = _ui.value.section
        viewModelScope.launch {
            _ui.update { it.copy(loadingContent = true) }
            if (section == MobileSection.LIVE) {
                repo.getChannels(id, page).fold(
                    onSuccess = { p ->
                        loading = false
                        if (id != curCat) return@fold
                        val existing = if (reset) emptyList() else _ui.value.channels
                        val seen = existing.mapTo(HashSet()) { it.id }
                        val combined = existing + p.items.filter { seen.add(it.id) }
                        // Some portals report totalItems as 0/absent. Trusting it blindly stopped
                        // paging after one page, so keep going while full pages keep arriving.
                        hasMore = p.items.isNotEmpty() &&
                            (p.totalItems <= 0 || combined.size < p.totalItems)
                        page += 1
                        _ui.update { it.copy(channels = combined, loadingContent = false) }
                    },
                    onFailure = { loading = false; _ui.update { it.copy(loadingContent = false) } }
                )
            } else {
                repo.getVodList(id, page).fold(
                    onSuccess = { p ->
                        loading = false
                        if (id != curCat) return@fold
                        val existing = if (reset) emptyList() else _ui.value.vod
                        val seen = existing.mapTo(HashSet()) { it.id }
                        val combined = existing + p.items.filter { seen.add(it.id) }
                        // Same guard as the channel branch above.
                        hasMore = p.items.isNotEmpty() &&
                            (p.totalItems <= 0 || combined.size < p.totalItems)
                        page += 1
                        _ui.update { it.copy(vod = combined, loadingContent = false) }
                    },
                    onFailure = { loading = false; _ui.update { it.copy(loadingContent = false) } }
                )
            }
        }
    }

    /** Hands the current channel list + index to the player so in-player next/prev matches. */
    fun prepareLivePlayback(index: Int) {
        liveContext.set(_ui.value.channels, index)
    }
}
