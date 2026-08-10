package com.tenkultra.tv.data.api.models

import com.google.gson.annotations.SerializedName

/** `{ "js": { total_items, max_page_items, data: [ channel … ] } }` */
data class ChannelListResponse(
    @SerializedName("js") val js: ChannelListData?
)

data class ChannelListData(
    @SerializedName("total_items") val totalItems: Int?,
    @SerializedName("max_page_items") val maxPageItems: Int?,
    @SerializedName("data") val data: List<ChannelDto>?
)

data class ChannelDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("number") val number: String?,
    @SerializedName("cmd") val cmd: String?,
    @SerializedName("logo") val logo: String?,
    @SerializedName("hd") val hd: String?,
    @SerializedName("cur_playing") val curPlaying: String?,
    @SerializedName("tv_genre_id") val genreId: String?
)

/** `{ "js": [ { name, t_time, … } ] }` from action=get_short_epg. */
data class ShortEpgResponse(
    @SerializedName("js") val js: List<EpgProgramDto>?
)

data class EpgProgramDto(
    @SerializedName("name") val name: String?,
    @SerializedName("descr") val descr: String?,
    @SerializedName("t_time") val tTime: String?,
    @SerializedName("t_time_to") val tTimeTo: String?,
    @SerializedName("time") val time: String?
)

/** `{ "js": { "cmd": "http://…m3u8", … } }` from action=create_link. */
data class CreateLinkResponse(
    @SerializedName("js") val js: CreateLinkData?
)

data class CreateLinkData(
    @SerializedName("cmd") val cmd: String?,
    @SerializedName("error") val error: String?
)
