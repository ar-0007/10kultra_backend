package com.tenkultra.tv.domain.model

/** A configured Stalker portal connection. */
data class Portal(
    val url: String,
    val macAddress: String,
    val token: String? = null
)
