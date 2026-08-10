package com.tenkultra.tv.data.repository

import android.util.Log
import com.tenkultra.tv.data.api.SessionManager
import com.tenkultra.tv.data.api.StalkerApiService
import com.tenkultra.tv.data.api.StalkerEndpoints
import com.tenkultra.tv.data.datastore.ContentCache
import com.tenkultra.tv.data.datastore.SettingsDataStore
import com.tenkultra.tv.data.api.models.ChannelDto
import com.tenkultra.tv.data.api.models.EpisodeDto
import com.tenkultra.tv.data.api.models.SeasonDto
import com.tenkultra.tv.data.api.models.VodItemDto
import com.tenkultra.tv.domain.model.Category
import com.tenkultra.tv.domain.model.Channel
import com.tenkultra.tv.domain.model.ChannelPage
import com.tenkultra.tv.domain.model.Episode
import com.tenkultra.tv.domain.model.Season
import com.tenkultra.tv.domain.model.VodItem
import com.tenkultra.tv.domain.model.VodPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val api: StalkerApiService,
    private val session: SessionManager,
    private val settings: SettingsDataStore,
    private val contentCache: ContentCache,
    private val authRepository: AuthRepository
) {
    /** Instant cached lists for snappy reopen (refreshed from network in the background). */
    suspend fun cachedTvGenres(): List<Category> = contentCache.getTvGenres()
    suspend fun cachedVodCategories(): List<Category> = contentCache.getVodCategories()

    private fun base(): String = session.endpointBase

    // ---- In-memory session caches (avoid re-hitting the portal for the same thing) ----
    // Stream links carry a short-lived token, so their cache TTL is tiny — just long enough to
    // let the side-preview's resolved link be reused when the user presses OK (instead of a 2nd
    // create_link round-trip for the SAME channel). Recovery / warm-keeper bypass this on purpose.
    // Seasons/episodes are static per series, so they cache for the whole session window — reopening
    // a series or flipping back to a season is then instant instead of a fresh portal call.
    private class Cached<T>(val value: T, val at: Long)
    private val streamLinkCache = java.util.concurrent.ConcurrentHashMap<String, Cached<String>>()
    private val seasonsCache = java.util.concurrent.ConcurrentHashMap<String, Cached<List<Season>>>()
    private val episodesCache = java.util.concurrent.ConcurrentHashMap<String, Cached<List<Episode>>>()
    // Browse-list pages (channels per genre, movies/series per category). Reopening a genre or
    // scrolling back to an already-seen page then paints INSTANTLY from memory instead of waiting
    // on a fresh portal round-trip every time — the slow-list-load the client hit. Short TTL keeps
    // it fresh; search results are never cached (they're query-specific and change constantly).
    private val channelPageCache = java.util.concurrent.ConcurrentHashMap<String, Cached<ChannelPage>>()
    private val vodPageCache = java.util.concurrent.ConcurrentHashMap<String, Cached<VodPage>>()

    private fun <T> Cached<T>?.fresh(ttlMs: Long): T? =
        this?.takeIf { System.currentTimeMillis() - it.at < ttlMs }?.value

    private companion object {
        const val STREAM_LINK_TTL_MS = 7_000L       // a warmed link is only reused while still FRESH. Measured on the live portal (2026-07-27): an UNPLAYED create_link token dies in UNDER ~10s (warmed links used 11-13s later 403'd the HLS segments), so 15s was too generous — anything older than 7s is treated stale and re-resolved fresh. Combined with consume-on-read this makes a dead-link first attempt rare.
        const val SERIES_TTL_MS = 10 * 60_000L      // seasons/episodes don't change within a session
        const val LIST_TTL_MS = 3 * 60_000L         // channel/movie list pages: fresh enough, instant reopen
    }

    /**
     * Re-establishes the session if the OS recreated the process while backgrounded.
     * [SessionManager] is in-memory and is normally seeded by Splash; but when Android
     * kills a backgrounded app and the user returns, Navigation restores straight to Home
     * (Splash never runs) → mac/token/base are empty and every call would fail silently.
     * This loads the MAC and re-handshakes (using the cached base) so content loads
     * WITHOUT needing a force-stop.
     */
    // Serialises session recovery so parallel first-loads (Home fires getTvGenres + getVodCategories
    // at once) don't each fire their own full handshake — only ONE re-handshake runs, the rest wait.
    private val sessionMutex = Mutex()

    private suspend fun ensureSession() {
        if (session.mac.isBlank()) {
            session.mac = settings.getOrCreateMac()
        }
        if (session.endpointBase.isBlank() || session.token.isBlank()) {
            sessionMutex.withLock {
                // Re-check inside the lock — another caller may have just handshaken.
                if (session.endpointBase.isBlank() || session.token.isBlank()) {
                    val portal = settings.portalUrlFlow.first()
                    if (!portal.isNullOrBlank()) authRepository.handshake(portal)
                }
            }
        }
    }

    /**
     * Runs an API call; heals the session first (process-death recovery). On failure it
     * re-handshakes ONCE and retries a single time — enough to clear the portal's transient
     * plain-text "Authorization failed" flap after a token rotates, without stacking multiple
     * full handshakes (which multiplied latency and flooded the portal). A CancellationException
     * (the user navigated away / switched channel) is rethrown immediately — never retried.
     */
    private suspend fun <T> withRefresh(block: suspend () -> T): T {
        ensureSession()
        try {
            return block()
        } catch (e: CancellationException) {
            throw e   // not a failure — the caller was cancelled; don't waste a handshake
        } catch (e: Exception) {
            // One re-auth + retry. (Was 3 attempts each re-handshaking — that could stall the UI
            // for a minute+ against a slow/dead host and hammer the portal.)
            val portal = settings.portalUrlFlow.first()
            if (!portal.isNullOrBlank()) authRepository.handshake(portal)
            delay(300L)
            return block()
        }
    }

    suspend fun getTvGenres(): Result<List<Category>> = withContext(Dispatchers.IO) {
        runCatching {
            withRefresh { api.getGenres(StalkerEndpoints.getGenres(base())) }.js.orEmpty()
                .mapNotNull { it.toCategory() }
                .also { contentCache.saveTvGenres(it) }
        }
    }

    suspend fun getVodCategories(): Result<List<Category>> = withContext(Dispatchers.IO) {
        runCatching {
            withRefresh { api.getVodCategories(StalkerEndpoints.getVodCategories(base())) }.js.orEmpty()
                .mapNotNull { it.toCategory() }
                .also { contentCache.saveVodCategories(it) }
        }
    }

    /** True if a title looks adult (18+/XXX/Adult…) — gated behind the parental PIN. */
    fun isAdultTitle(title: String): Boolean =
        com.tenkultra.tv.data.parental.ParentalControl.matches(title)

    suspend fun getShortEpg(channelId: String): Result<List<com.tenkultra.tv.domain.model.EpgProgram>> =
        withContext(Dispatchers.IO) {
            runCatching {
                withRefresh { api.getShortEpg(StalkerEndpoints.getShortEpg(base(), channelId)) }
                    .js.orEmpty().map { dto ->
                        val t = dto.tTime?.takeIf { it.isNotBlank() }
                            ?: dto.time?.let { runCatching { it.substring(11, 16) }.getOrNull() }
                            ?: "--:--"
                        com.tenkultra.tv.domain.model.EpgProgram(
                            time = t,
                            title = dto.name?.trim()?.takeIf { it.isNotEmpty() } ?: "No details available",
                            description = dto.descr?.trim().orEmpty()
                        )
                    }
            }
        }

    suspend fun getChannels(genreId: String, page: Int): Result<ChannelPage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cacheKey = "$genreId|$page"
                channelPageCache[cacheKey].fresh(LIST_TTL_MS)?.let { return@runCatching it }
                val resp = withRefresh {
                    api.getItvList(StalkerEndpoints.getItvList(base(), genreId, page))
                }.js
                ChannelPage(
                    items = resp?.data.orEmpty().map { it.toChannel() },
                    totalItems = resp?.totalItems ?: 0,
                    maxPageItems = resp?.maxPageItems ?: 14,
                    currentPage = page
                ).also {
                    if (it.items.isNotEmpty()) channelPageCache[cacheKey] = Cached(it, System.currentTimeMillis())
                }
            }
        }

    /**
     * Resolves a playable stream URL via create_link, PORTAL-AGNOSTICALLY (works like a generic STB
     * client on any Stalker portal — not just star/p1). [type] is "itv" or "vod", [cmd] is the
     * channel/movie command, [series] selects an episode for series. If the portal answers in an odd
     * shape we capture the raw body and, on total failure, throw a screenshot-able diagnostic.
     */
    suspend fun createStreamLink(
        type: String,
        cmd: String,
        series: Int? = null,
        useCache: Boolean = false,
        /**
         * true when the caller will PLAY the returned link. Stalker links are effectively
         * single-use — once played, the token is consumed and reusing the URL 403s the HLS
         * segments (the "plays the first time, not the second" bug). So a consuming read REMOVES
         * the cache entry (each warmed link is handed out exactly once), and a consuming fetch
         * does NOT store its result (an about-to-be-played link must never be reused later).
         * Background warms leave this false so their unplayed links stay shareable.
         */
        consume: Boolean = false
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cacheKey = "$type|$cmd|$series"
                if (useCache) {
                    val hit = if (consume) streamLinkCache.remove(cacheKey) else streamLinkCache[cacheKey]
                    hit.fresh(STREAM_LINK_TTL_MS)?.let { return@runCatching it }
                }
                val reqUrl = StalkerEndpoints.createLink(base(), type, cmd, series)
                // Ask for the playable link. Some portals answer create_link with a bare URL / plain
                // text (not the expected JSON) — if the typed parse fails, grab the RAW body so we can
                // still salvage a URL from it AND show exactly what came back.
                val rawCmd: String
                val rawError: String?
                val parsed = runCatching { withRefresh { api.createLink(reqUrl) }.js }
                if (parsed.isSuccess) {
                    rawCmd = parsed.getOrNull()?.cmd?.trim().orEmpty()
                    rawError = parsed.getOrNull()?.error
                } else {
                    val ex = parsed.exceptionOrNull()
                    if (ex is CancellationException) throw ex
                    // Consume the body INSIDE withRefresh so a retry doesn't leak an unread response.
                    val body = runCatching { withRefresh { api.createLinkRaw(reqUrl).string() } }
                        .getOrNull()?.trim().orEmpty()
                    rawCmd = body
                    rawError = ex?.message
                }
                Log.d("UltraStream", "type=$type series=$series cmd=$cmd -> returned='$rawCmd' err='$rawError'")
                var resolved = resolvePlayableUrl(rawCmd)
                // VOD MOVIE fallback: many portals list a movie cmd (/media/<id>.mpg) that create_link
                // REJECTS ("nothing_to_play") — the real file must be resolved via movie_id and played
                // as /media/file_<fileRowId>.mpg (same shape as series episodes). Additive: portals
                // whose direct cmd already resolves (e.g. the default/star portal) never reach this.
                if (resolved == null && type == "vod" && (series == null || series == 0)) {
                    val fileCmd = resolveVodFileCmd(cmd)
                    if (!fileCmd.isNullOrBlank() && fileCmd != cmd) {
                        val raw2 = runCatching {
                            withRefresh { api.createLink(StalkerEndpoints.createLink(base(), type, fileCmd, null)) }.js
                        }.getOrNull()
                        Log.d("UltraStream", "VOD fallback fileCmd=$fileCmd -> returned='${raw2?.cmd}' err='${raw2?.error}'")
                        resolved = raw2?.cmd?.trim()?.let { resolvePlayableUrl(it) }
                    }
                }
                // DIRECT-URL fallback: some portals hand back a cmd that is ALREADY a fully-qualified
                // media URL (e.g. a series episode cmd http://cdn/.../video.m3u8). create_link then
                // answers "nothing_to_play" — there's nothing to resolve — so just play the cmd's own
                // URL. Excludes localhost (ffmpeg http://localhost/ch/… genuinely needs create_link).
                if (resolved == null) {
                    val direct = resolvePlayableUrl(cmd)
                    if (direct != null && direct.startsWith("http", true) &&
                        !direct.contains("localhost") && !direct.contains("127.0.0.1")
                    ) {
                        Log.d("UltraStream", "direct-URL fallback: playing cmd as-is -> $direct")
                        resolved = direct
                    }
                }
                val finalUrl = resolved
                    ?: error(streamDiagnostic(type, cmd, series, reqUrl, rawCmd, rawError))
                if (!consume) streamLinkCache[cacheKey] = Cached(finalUrl, System.currentTimeMillis())
                finalUrl
            }
        }

    /**
     * Turns whatever create_link returns into a playable URL — portal-agnostic. Handles: a plain
     * http(s) URL; the common "<engine> http://…" form (ffmpeg/ffrt/auto/mpegts/…, take from the
     * first http); other protocols (rtmp/rtsp/udp); protocol-relative //host; and a root-relative
     * /path (prepend the portal host). Returns null only when there's no URL at all (→ diagnostic).
     */
    private fun resolvePlayableUrl(rawCmd: String): String? {
        var s = rawCmd.trim()
        if (s.isBlank()) return null
        // Strip a leading stream-engine token ("ffmpeg http…", "ffrt /media…", "auto …", "mpegts …")
        // ONLY when what follows is clearly a URL/path — so a plain-text error ("Authorization
        // failed. 75") is left intact and falls through to the diagnostic.
        if (!s.startsWith("http", true) && !s.startsWith("/")) {
            val sp = s.indexOf(' ')
            if (sp in 1..15) {
                val rest = s.substring(sp + 1).trim()
                if (rest.startsWith("http", true) || rest.startsWith("//") || rest.startsWith("/") ||
                    rest.startsWith("rtmp", true) || rest.startsWith("rtsp", true) || rest.startsWith("udp", true)
                ) s = rest
            }
        }
        return when {
            s.startsWith("http", true) -> s                              // plain http(s) URL
            s.startsWith("//") -> "http:$s"                              // protocol-relative //host/…
            s.startsWith("/") -> StalkerEndpoints.rootOf(base()) + s     // root-relative /media/… → portal host
            s.startsWith("rtmp", true) || s.startsWith("rtsp", true) || s.startsWith("udp", true) -> s
            s.contains("http") -> "http" + s.substringAfter("http")     // last resort: URL embedded mid-string
            else -> null                                                // no URL at all → diagnostic
        }
    }

    /**
     * Resolves a VOD MOVIE's real playable file cmd via movie_id — for portals whose category-list
     * cmd (`/media/<id>.mpg`) isn't directly playable. Returns `/media/file_<fileRowId>.mpg`, or null.
     */
    private suspend fun resolveVodFileCmd(movieCmd: String): String? {
        val movieId = Regex("""\d{2,}""").find(movieCmd)?.value ?: return null
        val files = runCatching {
            withRefresh { api.getOrderedList(StalkerEndpoints.getVodFiles(base(), movieId)) }.js?.data
        }.getOrNull().orEmpty()
        val fileId = files.firstNotNullOfOrNull { it.id?.trim()?.takeIf { id -> id.isNotBlank() } } ?: return null
        return "/media/file_$fileId.mpg"
    }

    /** A readable, screenshot-able play failure so a remote box's problem is diagnosable without adb. */
    private fun streamDiagnostic(
        type: String, cmd: String, series: Int?, reqUrl: String, rawCmd: String, rawError: String?
    ): String = buildString {
        append("Couldn't play this ${if (type == "itv") "channel" else "title"}.\n")
        rawError?.takeIf { it.isNotBlank() && !it.contains("BEGIN_OBJECT") }?.let { append("Portal: $it\n") }
        append("\n— diagnostic (screenshot & send) —\n")
        append("type: $type")
        if (series != null && series > 0) append("   series: $series")
        append("\nsent cmd: ${cmd.ifBlank { "(EMPTY — list gave no play command)" }}\n")
        append("portal returned: ${rawCmd.ifBlank { "(empty)" }.take(220)}\n")
        append("request: ${reqUrl.substringBefore("&JsHttpRequest")}")
    }

    suspend fun getSeasons(movieId: String, categoryId: String): Result<List<Season>> =
        withContext(Dispatchers.IO) {
            runCatching {
                seasonsCache[movieId].fresh(SERIES_TTL_MS)?.let { return@runCatching it }
                withRefresh { api.getSeasons(StalkerEndpoints.getSeasons(base(), movieId, categoryId)) }
                    .js?.data.orEmpty().map { it.toSeason() }
                    .also { if (it.isNotEmpty()) seasonsCache[movieId] = Cached(it, System.currentTimeMillis()) }
            }
        }

    /**
     * Episodes of a season. [seasonCmd] is the season's real play command, used as the
     * fallback cmd for any episode the portal returns without its own `cmd`.
     */
    suspend fun getEpisodes(movieId: String, seasonId: String, seasonCmd: String): Result<List<Episode>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val key = "$movieId|$seasonId"
                episodesCache[key].fresh(SERIES_TTL_MS)?.let { return@runCatching it }
                withRefresh { api.getEpisodes(StalkerEndpoints.getEpisodes(base(), movieId, seasonId)) }
                    .js?.data.orEmpty().mapIndexed { index, dto -> dto.toEpisode(index, seasonCmd) }
                    .sortedBy { it.number }
                    .also { if (it.isNotEmpty()) episodesCache[key] = Cached(it, System.currentTimeMillis()) }
            }
        }

    /**
     * Resolves an episode's REAL playable command the way STB clients do: re-query the season
     * with `&episode_id=<id>` so the portal returns the actual file row(s) carrying the true
     * `cmd` (e.g. `/media/file_<fileId>.mpg`). This replaces fabricating `/media/file_<id>.mpg`
     * from the list id — that guessed id often resolves to unrelated content, which is why some
     * episodes played a random episode/movie. Returns the real cmd; caller plays it with series=0.
     */
    suspend fun resolveEpisodeCmd(movieId: String, seasonId: String, episodeId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                Log.d("UltraStream", "RESOLVE  movie=$movieId season=$seasonId episode=$episodeId")
                val files = withRefresh {
                    api.getEpisodeFile(StalkerEndpoints.getEpisodeFile(base(), movieId, seasonId, episodeId))
                }.js?.data.orEmpty()
                // Pick the first file row that carries a play cmd OR a file id.
                val row = files.firstOrNull { !it.cmd.isNullOrBlank() || !it.id.isNullOrBlank() }
                    ?: error("Portal returned no file for episode $episodeId")
                val rowCmd = row.cmd?.trim().orEmpty()
                val fileId = row.id?.trim().orEmpty()
                // Portal-agnostic resolution:
                //  • A Stalker-style cmd ("/media/…", "ffmpeg …", "ffrt …") is create_link-able as-is → keep it.
                //  • A BARE direct URL (http…/video.m3u8 with NO token) is NOT playable — the CDN 403s it.
                //    Rebuild it as /media/file_<fileId>.mpg so create_link returns the TOKENIZED, playable
                //    URL (exactly how movies resolve). Healthy portals keep their real cmd unchanged.
                val realCmd = when {
                    rowCmd.isNotEmpty() && !rowCmd.startsWith("http", true) -> rowCmd
                    fileId.isNotEmpty() -> "/media/file_$fileId.mpg"
                    rowCmd.isNotEmpty() -> rowCmd
                    else -> error("Portal returned no file cmd for episode $episodeId")
                }
                Log.d("UltraStream", "RESOLVED cmd=$realCmd (rowCmd=$rowCmd fileId=$fileId)")
                realCmd
            }
        }

    suspend fun getVodList(categoryId: String, page: Int): Result<VodPage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cacheKey = "$categoryId|$page"
                vodPageCache[cacheKey].fresh(LIST_TTL_MS)?.let { return@runCatching it }
                val resp = withRefresh {
                    api.getOrderedList(StalkerEndpoints.getOrderedList(base(), categoryId, page))
                }.js
                VodPage(
                    items = resp?.data.orEmpty().map { it.toVodItem() },
                    totalItems = resp?.totalItems ?: 0,
                    maxPageItems = resp?.maxPageItems ?: 14,
                    currentPage = page
                ).also {
                    if (it.items.isNotEmpty()) vodPageCache[cacheKey] = Cached(it, System.currentTimeMillis())
                }
            }
        }

    suspend fun searchVod(query: String, page: Int): Result<VodPage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = withRefresh {
                    api.getOrderedList(StalkerEndpoints.searchVod(base(), query, page))
                }.js
                VodPage(
                    items = resp?.data.orEmpty().map { it.toVodItem() },
                    totalItems = resp?.totalItems ?: 0,
                    maxPageItems = resp?.maxPageItems ?: 14,
                    currentPage = page
                )
            }
        }

    private fun com.tenkultra.tv.data.api.models.StalkerCategory.toCategory(): Category? {
        val cid = id ?: return null
        return Category(id = cid, title = title?.trim().orEmpty().ifEmpty { "—" })
    }

    private fun EpisodeDto.toEpisode(index: Int, seasonCmd: String): Episode {
        // Episode number: portal's series_number, else the first value of its series[] array,
        // else 1-based position in the list.
        val num = seriesNumber?.toIntOrNull() ?: series?.firstOrNull() ?: (index + 1)
        val episodeId = id?.trim().orEmpty()
        return Episode(
            id = episodeId,
            number = num,
            title = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Episode $num",
            partCount = seriesFiles?.toIntOrNull() ?: 1,
            // Playable cmd priority:
            //  1) the portal's real per-episode cmd, if it returns one;
            //  2) the season/movie cmd fallback.
            // NOTE: we no longer fabricate `/media/file_<episodeId>.mpg`. That guessed the file id
            // from the list id and often resolved to unrelated content (random-episode bug). When
            // the portal gives no cmd, playback resolves the REAL cmd on demand via the episode's
            // `id` + `resolveEpisodeCmd` (the &episode_id= call). See [[series-cmd-quirk]].
            cmd = cmd?.trim()?.takeIf { it.isNotEmpty() } ?: seasonCmd
        )
    }

    private fun SeasonDto.toSeason(): Season = Season(
        id = id.orEmpty(),
        number = seasonNumber?.toIntOrNull() ?: 1,
        name = seasonName?.trim().orEmpty(),
        episodeCount = seasonSeries?.toIntOrNull() ?: series?.size ?: 0,
        videoId = videoId.orEmpty(),
        cmd = cmd?.trim().orEmpty(),
        episodeNumbers = series.orEmpty()
    )

    private fun ChannelDto.toChannel(): Channel {
        val logo = logo?.takeIf { it.isNotBlank() }?.let { l ->
            if (l.startsWith("http")) l
            else StalkerEndpoints.rootOf(base()) + "/stalker_portal/misc/logos/320/" + l
        }
        return Channel(
            id = id.orEmpty(),
            name = name?.trim().orEmpty(),
            number = number.orEmpty(),
            cmd = cmd.orEmpty(),
            logoUrl = logo,
            isHd = hd == "1",
            nowPlaying = curPlaying?.trim().orEmpty()
        )
    }

    private fun VodItemDto.toVodItem(): VodItem {
        val poster = screenshotUri?.takeIf { it.isNotBlank() }?.let { uri ->
            if (uri.startsWith("http")) uri
            else StalkerEndpoints.rootOf(base()) + (if (uri.startsWith("/")) uri else "/$uri")
        }
        return VodItem(
            id = id.orEmpty(),
            name = (name ?: originalName).orEmpty(),
            year = year?.takeIf { it.isNotBlank() && it != "0" }.orEmpty(),
            genre = genresStr?.trim().orEmpty(),
            director = director?.trim().orEmpty(),
            durationMin = time?.takeIf { it.isNotBlank() }.orEmpty(),
            description = description?.trim().orEmpty(),
            posterUrl = poster,
            added = added.orEmpty(),
            isHd = (hd ?: 0) == 1,
            isSeries = isSeries == "1",
            ratingImdb = ratingImdb ?: 0.0,
            cmd = cmd.orEmpty()
        )
    }
}
