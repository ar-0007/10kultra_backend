package com.tenkultra.tv.data.api

import com.tenkultra.tv.domain.model.Channel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries the live-TV channel list + current index from the channel screen into the
 * player, so the user can flip to the next/previous channel without leaving playback.
 * In-memory only (a transient hand-off; not persisted).
 */
@Singleton
class LivePlaybackContext @Inject constructor() {
    @Volatile
    var channels: List<Channel> = emptyList()

    @Volatile
    var index: Int = 0

    /**
     * Loads the NEXT page of the genre behind [channels] and returns the UPDATED full list — so
     * the fullscreen player can keep surfing through the WHOLE genre, not just the pages the
     * list screen happened to have loaded when the user pressed OK (the "long forward gets
     * stuck" bug). Null when the hand-off can't page (favorites view / search results / rails).
     */
    @Volatile
    var loadMore: (suspend () -> List<Channel>)? = null

    fun set(list: List<Channel>, startIndex: Int, loadMore: (suspend () -> List<Channel>)? = null) {
        channels = list
        index = startIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        this.loadMore = loadMore
    }

    fun channelAt(i: Int): Channel? = channels.getOrNull(i)
}
