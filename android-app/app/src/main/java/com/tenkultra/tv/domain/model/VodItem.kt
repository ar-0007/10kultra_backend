package com.tenkultra.tv.domain.model

/** A VOD entry (movie / series) in the Video Club list. */
data class VodItem(
    val id: String,
    val name: String,
    val year: String,
    val genre: String,
    val director: String,
    val durationMin: String,
    val description: String,
    val posterUrl: String?,
    val added: String,
    val isHd: Boolean,
    val isSeries: Boolean,
    val ratingImdb: Double,
    val cmd: String
)

/** One page of VOD results. */
data class VodPage(
    val items: List<VodItem>,
    val totalItems: Int,
    val maxPageItems: Int,
    val currentPage: Int
) {
    val totalPages: Int
        get() = if (maxPageItems <= 0) 1 else ((totalItems + maxPageItems - 1) / maxPageItems).coerceAtLeast(1)
}
