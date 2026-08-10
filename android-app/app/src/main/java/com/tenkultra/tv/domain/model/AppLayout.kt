package com.tenkultra.tv.domain.model

/** Selectable home/browse layouts. Persisted in DataStore. */
enum class AppLayout {
    CLASSIC,   // STBEMU carousel + centered category popup
    MODERN,    // Stalker-Player style: left sidebar + poster grid
    CINEMATIC; // Netflix/Google-TV style: hero banner + horizontal rails

    companion object {
        val DEFAULT = CLASSIC

        fun fromName(name: String?): AppLayout =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
