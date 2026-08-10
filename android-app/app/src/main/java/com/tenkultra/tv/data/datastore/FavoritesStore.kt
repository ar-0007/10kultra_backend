package com.tenkultra.tv.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tenkultra.tv.domain.model.Channel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.favoritesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "tenkultra_favorites")

/**
 * Persists the user's favourite live channels as an ORDERED list — order matters because
 * the user reorders favourites with the Move (blue) button. Stored as the full [Channel]
 * JSON (not just ids) so a favourite still shows in the FAVORITES filter even when it isn't
 * part of the currently loaded genre/page.
 */
@Singleton
class FavoritesStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val ds = context.favoritesDataStore

    val favoritesFlow: Flow<List<Channel>> = ds.data.map { prefs -> decode(prefs[KEY_FAVORITES]) }

    private suspend fun current(): List<Channel> = decode(ds.data.map { it[KEY_FAVORITES] }.first())

    private fun decode(json: String?): List<Channel> =
        json?.let { runCatching { gson.fromJson<List<Channel>>(it, LIST_TYPE) }.getOrNull() }.orEmpty()

    /** Adds the channel if it isn't a favourite yet, or removes it if it already is. */
    suspend fun toggle(channel: Channel) {
        val list = current().toMutableList()
        val existing = list.indexOfFirst { it.id == channel.id }
        if (existing >= 0) list.removeAt(existing) else list.add(channel)
        save(list)
    }

    /** Reorders a favourite by [delta] positions (used by the Move button; -1 = up, +1 = down). */
    suspend fun move(fromIndex: Int, delta: Int) {
        val list = current().toMutableList()
        val to = fromIndex + delta
        if (fromIndex in list.indices && to in list.indices) {
            list.add(to, list.removeAt(fromIndex))
            save(list)
        }
    }

    private suspend fun save(list: List<Channel>) {
        ds.edit { it[KEY_FAVORITES] = gson.toJson(list) }
    }

    private companion object {
        val KEY_FAVORITES = stringPreferencesKey("favorite_channels")
        val LIST_TYPE = object : TypeToken<List<Channel>>() {}.type
    }
}
