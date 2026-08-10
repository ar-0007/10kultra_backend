package com.tenkultra.tv.domain.model

/** A live TV channel. */
data class Channel(
    val id: String,
    val name: String,
    val number: String,
    val cmd: String,
    val logoUrl: String?,
    val isHd: Boolean,
    val nowPlaying: String
)

data class ChannelPage(
    val items: List<Channel>,
    val totalItems: Int,
    val maxPageItems: Int,
    val currentPage: Int
)

/** A single EPG (TV guide) program entry for a channel. */
data class EpgProgram(
    val time: String,
    val title: String,
    val description: String
)
