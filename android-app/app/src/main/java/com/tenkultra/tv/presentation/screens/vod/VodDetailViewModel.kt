package com.tenkultra.tv.presentation.screens.vod

import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenkultra.tv.data.api.VodPlaybackContext
import com.tenkultra.tv.domain.model.VodItem
import com.tenkultra.tv.data.repository.ContentRepository
import com.tenkultra.tv.domain.model.Episode
import com.tenkultra.tv.domain.model.Season
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Payload passed from the VOD list to the detail screen (base64 JSON in the route). */
data class VodDetailArgs(
    val movieId: String = "",
    val categoryId: String = "",
    val name: String = "",
    val year: String = "",
    val genre: String = "",
    val description: String = "",
    val posterUrl: String? = null,
    val cmd: String = "",
    val isSeries: Boolean = false
)

data class VodDetailUiState(
    val args: VodDetailArgs = VodDetailArgs(),
    val seasons: List<Season> = emptyList(),
    val selectedSeasonIndex: Int = 0,
    val episodes: List<Episode> = emptyList(),
    val loading: Boolean = true,
    val loadingEpisodes: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class VodDetailViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val gson: Gson,
    private val vodContext: VodPlaybackContext,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val args: VodDetailArgs = runCatching {
        val json = String(
            Base64.decode(
                savedStateHandle["payload"] ?: "",
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
        )
        gson.fromJson(json, VodDetailArgs::class.java)
    }.getOrDefault(VodDetailArgs())

    private val _uiState = MutableStateFlow(VodDetailUiState(args = args, loading = args.isSeries))
    val uiState = _uiState.asStateFlow()

    init {
        if (args.isSeries) loadSeasons() else _uiState.update { it.copy(loading = false) }
    }

    private fun loadSeasons() {
        viewModelScope.launch {
            contentRepository.getSeasons(args.movieId, args.categoryId).fold(
                onSuccess = { seasons ->
                    // Portal returns seasons newest-first; order ascending by number.
                    val ordered = seasons.sortedBy { it.number }
                    _uiState.update {
                        it.copy(
                            seasons = ordered,
                            selectedSeasonIndex = 0,
                            loading = false,
                            error = if (ordered.isEmpty()) "No seasons found." else null
                        )
                    }
                    ordered.firstOrNull()?.let { loadEpisodes(it) }
                    prefetchOtherSeasons(ordered)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(loading = false, error = friendlyError(e, "Failed to load seasons.")) }
                }
            )
        }
    }

    /**
     * Warm the OTHER seasons' episodes in the background (skipping season 1, which is already
     * loading) so switching seasons is instant. Each runs in its own coroutine → they resolve in
     * parallel, and [ContentRepository.getEpisodes] caches every result. Failures are ignored;
     * an un-warmed season simply loads on demand as before.
     */
    private fun prefetchOtherSeasons(seasons: List<Season>) {
        seasons.drop(1).forEach { s ->
            viewModelScope.launch {
                val cmd = s.cmd.ifBlank { args.cmd }
                contentRepository.getEpisodes(args.movieId, s.id, cmd)
            }
        }
    }

    fun selectSeason(index: Int) {
        val seasons = _uiState.value.seasons
        val clamped = index.coerceIn(0, seasons.lastIndex.coerceAtLeast(0))
        if (clamped == _uiState.value.selectedSeasonIndex && _uiState.value.episodes.isNotEmpty()) return
        _uiState.update { it.copy(selectedSeasonIndex = clamped, episodes = emptyList()) }
        seasons.getOrNull(clamped)?.let { loadEpisodes(it) }
    }

    private fun loadEpisodes(season: Season) {
        _uiState.update { it.copy(loadingEpisodes = true) }
        viewModelScope.launch {
            // Many portals (e.g. p1.airce.io) give seasons AND episodes NO `cmd` of their own —
            // the only playable command is the series/movie-level cmd, with create_link&series=<n>
            // selecting the episode. Fall back season.cmd → movie cmd so episodes are never blank.
            val episodeCmd = season.cmd.ifBlank { args.cmd }
            contentRepository.getEpisodes(args.movieId, season.id, episodeCmd).fold(
                onSuccess = { eps ->
                    if (season.id != _uiState.value.seasons.getOrNull(_uiState.value.selectedSeasonIndex)?.id) return@fold
                    // Some portals return the episode list only as the season's `series` number
                    // array; if the per-episode call came back empty, synthesize episodes from
                    // those numbers, all playing off the resolved cmd.
                    val resolved = eps.ifEmpty { synthesizeEpisodes(season, episodeCmd) }
                    _uiState.update {
                        it.copy(
                            episodes = resolved,
                            loadingEpisodes = false,
                            error = if (resolved.isEmpty()) "No episodes in this season." else null
                        )
                    }
                },
                onFailure = { e ->
                    // Network/parse failure: fall back to the season's series numbers if we have them.
                    val fallback = synthesizeEpisodes(season, episodeCmd)
                    _uiState.update {
                        it.copy(
                            episodes = fallback,
                            loadingEpisodes = false,
                            error = if (fallback.isEmpty()) friendlyError(e, "Failed to load episodes.") else null
                        )
                    }
                }
            )
        }
    }

    /**
     * Maps raw repository errors to something a viewer can act on. The portal sometimes answers
     * with a plain-text "Authorization failed. <n>" (not JSON), which Gson reports as
     * "Expected BEGIN_OBJECT but was STRING …" — meaningless to a user. Surface a clean,
     * retry-able message instead of the stack-trace text.
     */
    private fun friendlyError(e: Throwable, fallback: String): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("Authorization failed", ignoreCase = true) ->
                "Session expired. Please try again in a moment."
            msg.contains("BEGIN_OBJECT") || msg.contains("BEGIN_ARRAY") ||
                msg.contains("JSON", ignoreCase = true) || msg.contains("gson", ignoreCase = true) ->
                "The portal returned an unexpected response. Please try again."
            msg.isBlank() -> fallback
            else -> msg
        }
    }

    /** Builds episodes straight from the season's episode-number array + resolved play cmd. */
    private fun synthesizeEpisodes(season: Season, cmd: String): List<Episode> =
        season.episodeNumbers.sorted().map { n ->
            Episode(id = "", number = n, title = "Episode $n", partCount = 1, cmd = cmd)
        }

    /**
     * Resolves the episode's REAL play command before navigating to the player, then invokes
     * [onReady] with (cmd, seriesArg). When the episode has an `id`, ask the portal for the
     * authoritative file cmd (the &episode_id= call) and play it with series=0 — this fixes
     * episodes that jumped to a random episode/movie. If that fails (or there's no id, e.g. a
     * synthesized episode), fall back to the episode's own cmd + series index.
     */
    /**
     * Hands what is about to play to the player so it can build a "Continue watching" card —
     * the player route only carries cmd/title/series, which has no poster and no series flag.
     * [title] is the episode label for a series ("Show S1E3") or the movie name.
     */
    fun prepareVodPlayback(cmd: String, series: Int, title: String) {
        vodContext.set(
            VodItem(
                id = args.movieId,
                name = title,
                year = args.year,
                genre = args.genre,
                director = "",
                durationMin = "",
                description = args.description,
                posterUrl = args.posterUrl,
                added = "",
                isHd = false,
                isSeries = args.isSeries,
                ratingImdb = 0.0,
                cmd = cmd
            ),
            series
        )
    }

    fun resolveAndPlay(episode: Episode, season: Season?, onReady: (cmd: String, series: Int) -> Unit) {
        viewModelScope.launch {
            val seasonId = season?.id.orEmpty()
            val real = if (episode.id.isNotEmpty() && seasonId.isNotEmpty()) {
                contentRepository.resolveEpisodeCmd(args.movieId, seasonId, episode.id).getOrNull()
            } else null

            when {
                !real.isNullOrBlank() -> onReady(real, 0)
                // Resolve failed (usually a transient "Authorization failed" from the portal).
                // Fall back to the DETERMINISTIC per-episode file — the exact cmd the portal
                // itself returns on success (fileId == episodeId). It pinpoints the episode with
                // series=0 and does NOT depend on the flaky episode_id call. We must NEVER fall
                // back to the movie/season cmd + series index: the movie-level cmd ignores
                // `series`, so that path plays a fixed/wrong file — the random-episode bug.
                episode.id.isNotEmpty() -> onReady("/media/file_${episode.id}.mpg", 0)
                // A synthesized episode with no id at all: last resort. A real file cmd is already
                // episode-specific (series=0); anything else needs the series index.
                episode.cmd.startsWith("/media/file_") -> onReady(episode.cmd, 0)
                else -> onReady(episode.cmd, episode.number)
            }
        }
    }
}
