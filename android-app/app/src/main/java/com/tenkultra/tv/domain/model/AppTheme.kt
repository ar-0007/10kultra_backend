package com.tenkultra.tv.domain.model

/** Selectable UI themes. Persisted in DataStore. */
enum class AppTheme {
    BRAND,   // 10K Ultra onyx + gold brand style (default)
    CLASSIC, // STBEMU blue/dark style
    MODERN;  // Netflix dark style

    companion object {
        val DEFAULT = BRAND

        fun fromName(name: String?): AppTheme =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
