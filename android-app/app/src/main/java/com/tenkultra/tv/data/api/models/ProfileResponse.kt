package com.tenkultra.tv.data.api.models

import com.google.gson.annotations.SerializedName

/**
 * Response of action=get_profile. Returned after handshake to validate the token
 * and bind the MAC to the account. Only a subset of fields is modelled.
 */
data class ProfileResponse(
    @SerializedName("js") val js: ProfileData?
)

data class ProfileData(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("mac") val mac: String?,
    @SerializedName("status") val status: Int?,
    @SerializedName("blocked") val blocked: String?,
    @SerializedName("msg") val msg: String?,
    @SerializedName("block_msg") val blockMsg: String?
)
