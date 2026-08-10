package com.tenkultra.tv.data.parental

import com.tenkultra.tv.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CHILD LOCK — the single source of truth for "is this adult content, and is it locked right now?".
 *
 * The lock is CATEGORY-level: an adult CATEGORY / GENRE asks for the PIN when it is opened, in
 * EVERY layout (Classic / Modern / Cinematic / Mobile) and for both Live TV and Video Club —
 * before, only the Classic home asked, so every other layout let adult categories straight through.
 *
 * Unlocking with the PIN unlocks for the rest of the SESSION (until the app process restarts),
 * so a parent isn't re-prompted on every single item while browsing.
 */
@Singleton
class ParentalControl @Inject constructor(
    private val settings: SettingsDataStore
) {

    private val _sessionUnlocked = MutableStateFlow(false)
    val sessionUnlockedFlow: StateFlow<Boolean> = _sessionUnlocked

    /** True while adult content must be PIN-gated: the lock is on and no PIN was entered yet. */
    val restrictionActiveFlow: Flow<Boolean> =
        combine(settings.parentalLockFlow, _sessionUnlocked) { lockOn, unlocked -> lockOn && !unlocked }

    /** True if a title looks adult (18+/XXX/Adult…) — matched on categories, channels and movies. */
    fun isAdultTitle(title: String?): Boolean = matches(title)

    /** Suspending check used outside Compose (view models, player surfing). */
    suspend fun isLocked(title: String?): Boolean {
        if (_sessionUnlocked.value) return false
        if (!isAdultTitle(title)) return false
        return settings.parentalLockFlow.first()
    }

    /** Verifies the parental PIN; a match unlocks adult content for the rest of the session. */
    suspend fun verify(entered: String): Boolean {
        val stored = settings.parentalPinFlow.first()?.takeIf { it.isNotBlank() } ?: DEFAULT_PIN
        val ok = entered.trim() == stored
        if (ok) _sessionUnlocked.value = true
        return ok
    }

    /** Re-arms the lock (used when the parent turns the lock on again in Settings). */
    fun relock() { _sessionUnlocked.value = false }

    companion object {
        /** Until a parent sets their own PIN in Settings. */
        const val DEFAULT_PIN = "0000"

        /** The ONE adult-title test used everywhere (also by ContentRepository), so the keyword
         *  list can never drift between the lock and the rest of the app. */
        fun matches(title: String?): Boolean {
            val t = title?.uppercase()?.trim().orEmpty()
            if (t.isEmpty()) return false
            // Whole-word match, so "Essex"/"Middlesex" is NOT flagged as "SEX" but "Sex TV" is.
            return SUBSTRING_KEYWORDS.any { t.contains(it) } ||
                WORD_KEYWORDS.any { WORD_BOUNDARY_CACHE.getValue(it).containsMatchIn(t) }
        }

        /** Matched anywhere in the title — unambiguous markers. */
        val SUBSTRING_KEYWORDS = listOf(
            "ADULT", "18+", "+18", "21+", "XXX", "PORN", "EROTIC", "HOT 18", "FOR ADULTS",
            "BRAZZERS", "HUSTLER", "DORCEL", "REDLIGHT", "RED LIGHT", "PLAYBOY", "PENTHOUSE",
            "VIXEN", "BANG U", "PRIVATE TV", "BLUE HUSTLER", "DUSK", "O-LA-LA", "OLALA"
        )

        /** Matched as WHOLE WORDS only — short words that appear inside innocent names. */
        val WORD_KEYWORDS = listOf("SEX", "SEXY", "NUDE", "EROX", "VIVID", "BABE", "BABES", "X-RATED")

        private val WORD_BOUNDARY_CACHE: Map<String, Regex> =
            WORD_KEYWORDS.associateWith { Regex("(^|[^A-Z0-9])${Regex.escape(it)}([^A-Z0-9]|$)") }
    }
}
