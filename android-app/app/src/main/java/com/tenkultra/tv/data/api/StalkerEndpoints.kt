package com.tenkultra.tv.data.api

import java.net.URI
import java.net.URLEncoder

/**
 * Builds Stalker portal URLs. Different providers expose the middleware at
 * different paths (`/portal.php`, `/stalker_portal/server/load.php`, …), so the
 * working endpoint is *discovered* during handshake (see [handshakeCandidates])
 * and then reused for every later call via [action].
 */
object StalkerEndpoints {

    /** Scheme://host[:port] of the entered portal URL (no path). */
    fun rootOf(portalUrl: String): String {
        var u = portalUrl.trim()
        if (u.isEmpty()) return u
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) {
            u = "http://$u"
        }
        return try {
            val uri = URI(u)
            val port = if (uri.port != -1) ":${uri.port}" else ""
            "${uri.scheme}://${uri.host}$port"
        } catch (_: Exception) {
            u.substringBefore("?").trimEnd('/')
        }
    }

    /**
     * Candidate endpoint bases (the `*.php` file), most-standard first. If the
     * entered URL already names a `.php` endpoint, that is tried first.
     */
    fun candidateBases(portalUrl: String): List<String> {
        val root = rootOf(portalUrl)
        val cleaned = portalUrl.trim().substringBefore("?").substringBefore("#")
        val explicit = if (cleaned.endsWith(".php", true)) listOf(cleaned) else emptyList()
        return (explicit + listOf(
            "$root/stalker_portal/server/load.php",
            "$root/server/load.php",
            "$root/portal.php",
            "$root/c/portal.php",
            "$root/stalker_portal/c/portal.php"
        )).distinct()
    }

    /** Full handshake URLs for every candidate base. */
    fun handshakeCandidates(portalUrl: String): List<String> =
        candidateBases(portalUrl).map { handshake(it) }

    fun handshake(base: String): String =
        action(base, "type=stb&action=handshake&token=")

    fun getProfile(base: String): String =
        action(base, "type=stb&action=get_profile&hd=1")

    /** Subscriber account details: name, tariff/package, expiry date, phone. */
    fun getAccountInfo(base: String): String =
        action(base, "type=account_info&action=get_main_info")

    fun getAllChannels(base: String): String =
        action(base, "type=itv&action=get_all_channels")

    fun getGenres(base: String): String =
        action(base, "type=itv&action=get_genres")

    fun getItvList(base: String, genreId: String, page: Int): String =
        action(base, "type=itv&action=get_ordered_list&genre=$genreId&p=$page")

    fun getVodCategories(base: String): String =
        action(base, "type=vod&action=get_categories")

    fun getOrderedList(base: String, categoryId: String, page: Int): String =
        action(base, "type=vod&action=get_ordered_list&category=$categoryId&sortby=added&p=$page")

    /** VOD title search (portal-side). */
    fun searchVod(base: String, query: String, page: Int): String =
        action(base, "type=vod&action=get_ordered_list&search=${URLEncoder.encode(query, "UTF-8")}&p=$page")

    fun getSeasons(base: String, movieId: String, categoryId: String): String =
        action(base, "type=vod&action=get_ordered_list&movie_id=$movieId&category=$categoryId&p=1")

    /**
     * The playable FILE row(s) for a VOD MOVIE, resolved via movie_id. On many portals the category
     * list gives a movie cmd (`/media/<id>.mpg`) that create_link REJECTS ("nothing_to_play") — the
     * real file must be fetched here and played as `/media/file_<fileRowId>.mpg`. Mirrors how series
     * episodes resolve their file.
     */
    fun getVodFiles(base: String, movieId: String): String =
        action(base, "type=vod&action=get_ordered_list&movie_id=$movieId&p=1")

    fun getEpisodes(base: String, movieId: String, seasonId: String): String =
        action(base, "type=vod&action=get_ordered_list&movie_id=$movieId&season_id=$seasonId&p=1")

    /**
     * The file object(s) for ONE episode. Adding `episode_id` makes the portal return the
     * real playable FILE rows (with their own `cmd` like `/media/file_<fileId>.mpg`) instead
     * of the episode metadata. This is the canonical way STB clients resolve an episode's
     * stream — we use the returned `cmd` verbatim rather than constructing one.
     */
    fun getEpisodeFile(base: String, movieId: String, seasonId: String, episodeId: String): String =
        action(base, "type=vod&action=get_ordered_list&movie_id=$movieId&season_id=$seasonId&episode_id=$episodeId&p=1")

    fun getEpgInfo(base: String, channelId: String): String =
        action(base, "type=itv&action=get_epg_info&ch_id=$channelId")

    /** Short EPG: the next [size] programs for one channel (the TV-guide schedule). */
    fun getShortEpg(base: String, channelId: String, size: Int = 12): String =
        action(base, "type=itv&action=get_short_epg&ch_id=$channelId&size=$size")

    /** type is "itv" or "vod". [series] selects a series episode (null/0 for movies & channels). */
    fun createLink(base: String, type: String, cmd: String, series: Int? = null): String {
        val encoded = URLEncoder.encode(cmd, "UTF-8")
        val seriesParam = if (series != null && series > 0) "&series=$series" else ""
        return action(base, "type=$type&action=create_link&cmd=$encoded$seriesParam")
    }

    /** Appends a Stalker action query (plus the required JsHttpRequest marker) to a base. */
    private fun action(base: String, query: String): String =
        "$base?$query&JsHttpRequest=1-xml"
}
