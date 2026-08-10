package com.tenkultra.tv.data.api.models

import com.google.gson.annotations.SerializedName

/** A TV genre (get_genres) or VOD category (get_categories) — same shape. */
data class StalkerCategory(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("alias") val alias: String?
)

/** `{ "js": [ {id,title,…}, … ] }` */
data class CategoryListResponse(
    @SerializedName("js") val js: List<StalkerCategory>?
)

/** `{ "js": { total_items, max_page_items, data: [ … ] } }` */
data class VodListResponse(
    @SerializedName("js") val js: VodListData?
)

data class VodListData(
    @SerializedName("total_items") val totalItems: Int?,
    @SerializedName("max_page_items") val maxPageItems: Int?,
    @SerializedName("data") val data: List<VodItemDto>?
)

/** Seasons of a series, from get_ordered_list&movie_id=… */
data class SeasonListResponse(
    @SerializedName("js") val js: SeasonListData?
)

data class SeasonListData(
    @SerializedName("data") val data: List<SeasonDto>?
)

data class SeasonDto(
    @SerializedName("id") val id: String?,
    @SerializedName("video_id") val videoId: String?,
    @SerializedName("season_number") val seasonNumber: String?,
    @SerializedName("season_name") val seasonName: String?,
    @SerializedName("season_series") val seasonSeries: String?,
    // The portal's REAL play command for this season/series. Episode playback must
    // use this verbatim (never fabricate `/media/<id>.mpg`).
    @SerializedName("cmd") val cmd: String?,
    // Episode numbers available in this season, e.g. [1,2,3,…] (Stalker series array).
    @SerializedName("series") val series: List<Int>?
)

/** Episodes of a season, from get_ordered_list&movie_id=…&season_id=… */
data class EpisodeListResponse(
    @SerializedName("js") val js: EpisodeListData?
)

data class EpisodeListData(
    @SerializedName("data") val data: List<EpisodeDto>?
)

data class EpisodeDto(
    @SerializedName("id") val id: String?,
    @SerializedName("series_number") val seriesNumber: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("series_files") val seriesFiles: String?,
    @SerializedName("season_id") val seasonId: String?,
    // The portal's REAL play command for this episode (when the portal returns one
    // per episode). If blank, fall back to the parent season's cmd.
    @SerializedName("cmd") val cmd: String?,
    // Some portals also expose the episode-number array on the episode object.
    @SerializedName("series") val series: List<Int>?
)

data class VodItemDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("o_name") val originalName: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("year") val year: String?,
    @SerializedName("director") val director: String?,
    @SerializedName("actors") val actors: String?,
    @SerializedName("time") val time: String?,
    @SerializedName("genres_str") val genresStr: String?,
    @SerializedName("added") val added: String?,
    @SerializedName("hd") val hd: Int?,
    @SerializedName("is_series") val isSeries: String?,
    @SerializedName("rating_imdb") val ratingImdb: Double?,
    @SerializedName("screenshot_uri") val screenshotUri: String?,
    @SerializedName("cmd") val cmd: String?
)
