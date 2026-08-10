package com.tenkultra.tv.data.api

import com.tenkultra.tv.domain.model.VodItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries the VOD title the user just opened from a browse screen into the player — the player
 * route only has cmd/title/series, which isn't enough to build a "Continue watching" card
 * (it needs the poster and whether it's a series).
 *
 * In-memory only, mirroring [LivePlaybackContext]: a transient hand-off, never persisted. The
 * player matches it against the cmd it is actually playing, so a stale entry is simply ignored.
 */
@Singleton
class VodPlaybackContext @Inject constructor() {
    @Volatile
    var item: VodItem? = null
        private set

    /** Episode number when a series episode is playing (0 = a movie / not applicable). */
    @Volatile
    var series: Int = 0
        private set

    fun set(item: VodItem, series: Int = 0) {
        this.item = item
        this.series = series
    }

    fun clear() {
        item = null
        series = 0
    }
}
