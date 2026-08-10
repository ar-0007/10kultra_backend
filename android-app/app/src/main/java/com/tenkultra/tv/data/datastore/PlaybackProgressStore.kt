package com.tenkultra.tv.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.progressDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "tenkultra_playback_progress")

/**
 * Remembers how far the user watched each VOD movie / series episode, so playback can
 * resume from where it was left off instead of restarting. Keyed by the content's stable
 * cmd (NOT the tokenised stream URL). Live TV is never stored.
 */
@Singleton
class PlaybackProgressStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ds = context.progressDataStore

    suspend fun save(key: String, positionMs: Long) {
        ds.edit { it[longPreferencesKey(key)] = positionMs }
    }

    suspend fun get(key: String): Long =
        ds.data.map { it[longPreferencesKey(key)] ?: 0L }.first()

    suspend fun clear(key: String) {
        ds.edit { it.remove(longPreferencesKey(key)) }
    }
}
