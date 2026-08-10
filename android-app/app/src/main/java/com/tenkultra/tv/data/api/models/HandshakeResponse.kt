package com.tenkultra.tv.data.api.models

import com.google.gson.annotations.SerializedName

/** Response of action=handshake — `{ "js": { "token": "...", "random": "..." } }`. */
data class HandshakeResponse(
    @SerializedName("js") val js: HandshakeData?
)

data class HandshakeData(
    @SerializedName("token") val token: String?,
    @SerializedName("random") val random: String?
)
