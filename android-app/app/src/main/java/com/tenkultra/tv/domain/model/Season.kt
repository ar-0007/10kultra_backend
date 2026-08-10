package com.tenkultra.tv.domain.model

/** A season of a series, with its episode count. */
data class Season(
    val id: String,
    val number: Int,
    val name: String,
    val episodeCount: Int,
    val videoId: String,
    /** The portal's real play command for this season (fallback for its episodes). */
    val cmd: String = "",
    /** Episode numbers the portal reports for this season, e.g. [1,2,3,…]. */
    val episodeNumbers: List<Int> = emptyList()
)

/**
 * A single episode. [cmd] is the portal's REAL play command (from the season/episode
 * `cmd` field); [number] is the Stalker series index. Playback is
 * `create_link(type="vod", cmd=<cmd>, series=<number>)` — we never fabricate the cmd.
 */
data class Episode(
    val id: String,
    val number: Int,
    val title: String,
    val partCount: Int,
    val cmd: String = ""
)
