package com.tenkultra.tv.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tenkultra.tv.domain.model.VodItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.continueWatchingDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "tenkultra_continue_watching")

/**
 * One half-watched title, with just enough to draw its card and resume it.
 *
 * [progressKey] is the same key [PlaybackProgressStore] uses, so the two never disagree about
 * where playback should resume from. [series] is the episode number (0 for a movie).
 */
data class WatchedEntry(
    val item: VodItem,
    val series: Int,
    val progressKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) {
    /** 0f..1f — how far through the title the user is (0 when the duration isn't known yet). */
    val fraction: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * Backs the "Continue watching" rail: the most recently part-watched movies / episodes, newest
 * first. Written by the player as it saves progress and cleared when a title finishes, so the rail
 * only ever shows things there is actually something left to watch of.
 */
@Singleton
class ContinueWatchingStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val ds = context.continueWatchingDataStore

    val entriesFlow: Flow<List<WatchedEntry>> = ds.data.map { decode(it[KEY_ENTRIES]) }

    suspend fun entries(): List<WatchedEntry> = decode(ds.data.map { it[KEY_ENTRIES] }.first())

    /** Adds or refreshes [entry], moving it to the front and trimming the list to [MAX]. */
    suspend fun upsert(entry: WatchedEntry) {
        val list = entries().filterNot { it.progressKey == entry.progressKey }
        save(listOf(entry) + list.take(MAX - 1))
    }

    /** Drops a title from the rail (it finished, or the user removed it). */
    suspend fun remove(progressKey: String) {
        val list = entries()
        if (list.none { it.progressKey == progressKey }) return
        save(list.filterNot { it.progressKey == progressKey })
    }

    suspend fun clear() {
        ds.edit { it.remove(KEY_ENTRIES) }
    }

    private suspend fun save(list: List<WatchedEntry>) {
        ds.edit { it[KEY_ENTRIES] = gson.toJson(list) }
    }

    private fun decode(json: String?): List<WatchedEntry> =
        json?.let { runCatching { gson.fromJson<List<WatchedEntry>>(it, LIST_TYPE) }.getOrNull() }
            .orEmpty()
            .filter { it.item.cmd.isNotBlank() }

    private companion object {
        const val MAX = 20
        val KEY_ENTRIES = stringPreferencesKey("continue_watching")
        val LIST_TYPE = object : TypeToken<List<WatchedEntry>>() {}.type
    }
}
