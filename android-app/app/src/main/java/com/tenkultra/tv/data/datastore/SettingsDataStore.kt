package com.tenkultra.tv.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tenkultra.tv.domain.model.AppLayout
import com.tenkultra.tv.domain.model.AppTheme
import com.tenkultra.tv.domain.model.VideoQuality
import com.tenkultra.tv.util.MacAddress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tenkultra_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ds = context.dataStore

    val portalUrlFlow: Flow<String?> = ds.data.map { it[KEY_PORTAL_URL] }

    val macFlow: Flow<String?> = ds.data.map { it[KEY_MAC] }

    val themeFlow: Flow<AppTheme> = ds.data.map { AppTheme.fromName(it[KEY_THEME]) }

    val layoutFlow: Flow<AppLayout> = ds.data.map { AppLayout.fromName(it[KEY_LAYOUT]) }

    val videoQualityFlow: Flow<VideoQuality> = ds.data.map { VideoQuality.fromName(it[KEY_VIDEO_QUALITY]) }

    /** Parental control: 4-digit PIN (null/blank = not set) and the adult-content lock toggle. */
    val parentalPinFlow: Flow<String?> = ds.data.map { it[KEY_PARENTAL_PIN] }
    // Child lock is ON by default — adult content stays gated until a parent explicitly turns the
    // lock off in Settings (default PIN "0000" until they set their own).
    val parentalLockFlow: Flow<Boolean> = ds.data.map { it[KEY_PARENTAL_LOCK] != "0" }

    /** Last portal endpoint base that handshook successfully — tried first next time (faster startup). */
    val cachedBaseFlow: Flow<String?> = ds.data.map { it[KEY_CACHED_BASE] }

    suspend fun setPortalUrl(url: String) {
        ds.edit { it[KEY_PORTAL_URL] = url }
    }

    suspend fun setParentalPin(pin: String?) {
        ds.edit {
            if (pin.isNullOrBlank()) it.remove(KEY_PARENTAL_PIN) else it[KEY_PARENTAL_PIN] = pin.trim()
        }
    }

    suspend fun setParentalLock(enabled: Boolean) {
        ds.edit { it[KEY_PARENTAL_LOCK] = if (enabled) "1" else "0" }
    }

    suspend fun setCachedBase(base: String) {
        ds.edit { it[KEY_CACHED_BASE] = base }
    }

    /** Forgets the last-known working endpoint — call when the portal URL changes so the next
     *  handshake probes the NEW portal fresh instead of reconnecting to the old (still-alive) one. */
    suspend fun clearCachedBase() {
        ds.edit { it.remove(KEY_CACHED_BASE) }
    }

    suspend fun setTheme(theme: AppTheme) {
        ds.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setLayout(layout: AppLayout) {
        ds.edit { it[KEY_LAYOUT] = layout.name }
    }

    suspend fun setVideoQuality(quality: VideoQuality) {
        ds.edit { it[KEY_VIDEO_QUALITY] = quality.name }
    }

    /**
     * Returns the active MAC — UNIQUE per device and STABLE across app uninstall / reinstall /
     * update. It's derived deterministically from the device's stable [Settings.Secure.ANDROID_ID]
     * (via [MacAddress.forDevice]), so the same box always gets the same MAC and the provider only
     * needs to whitelist it once. A user-set MAC (if ever set) is still respected. We cache it in
     * DataStore for speed, but even if that's wiped the re-derived value is identical.
     */
    @Suppress("HardwareIds")
    suspend fun getOrCreateMac(): String {
        // FIXED-MAC BUILD (opt-in at build time only): `-PforcedMac=DA:7A:69:4E:DE:E0` bakes ONE
        // already-whitelisted MAC into that APK, so the box connects without the provider having
        // to whitelist a new address. Empty in every normal build → the unique per-device MAC
        // below is used, and the anti-clone guarantee is untouched.
        com.tenkultra.tv.BuildConfig.FORCED_MAC.takeIf { it.isNotBlank() }?.let { return it }
        // Respect a MAC we've already stored for this box (user-set, or previously derived) so
        // the box keeps ONE stable identity for its whole life — the provider whitelists it once.
        ds.data.first()[KEY_MAC]?.takeIf { it.isNotBlank() }?.let { return it }
        // First launch on this box: derive a UNIQUE + STABLE branded MAC from the device's
        // ANDROID_ID. Deterministic (survives reinstall / update) yet unique per device, so no
        // two boxes ever share a MAC. Guard against the well-known emulator/clone ANDROID_ID and
        // any blank value by falling back to a random UUID (still cached below, so it stays stable).
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        )?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
            ?: java.util.UUID.randomUUID().toString()
        val mac = MacAddress.forDevice(androidId)
        ds.edit { it[KEY_MAC] = mac }
        return mac
    }

    /** Saves a user-entered MAC. */
    suspend fun setMac(mac: String) {
        val v = mac.trim().uppercase()
        if (v.isNotBlank()) ds.edit { it[KEY_MAC] = v }
    }

    suspend fun clearPortal() {
        ds.edit {
            it.remove(KEY_PORTAL_URL)
        }
    }

    companion object {
        private val KEY_PORTAL_URL = stringPreferencesKey("portal_url")
        private val KEY_MAC = stringPreferencesKey("mac_address")
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_LAYOUT = stringPreferencesKey("layout")
        private val KEY_VIDEO_QUALITY = stringPreferencesKey("video_quality")
        private val KEY_PARENTAL_PIN = stringPreferencesKey("parental_pin")
        private val KEY_PARENTAL_LOCK = stringPreferencesKey("parental_lock")
        private val KEY_CACHED_BASE = stringPreferencesKey("cached_base")
    }
}
