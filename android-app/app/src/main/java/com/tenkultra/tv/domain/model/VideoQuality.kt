package com.tenkultra.tv.domain.model

/** Max video resolution cap applied to the player's track selector. */
enum class VideoQuality(val label: String, val maxHeight: Int) {
    AUTO("Auto (best)", Int.MAX_VALUE),
    HIGH("High — 1080p", 1080),
    MEDIUM("Medium — 720p", 720),
    LOW("Low — 480p (data saver)", 480);

    companion object {
        val DEFAULT = AUTO

        fun fromName(name: String?): VideoQuality =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
