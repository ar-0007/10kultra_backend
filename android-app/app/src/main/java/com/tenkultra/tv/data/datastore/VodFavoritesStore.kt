package com.tenkultra.tv.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tenkultra.tv.domain.model.VodItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vodFavoritesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "tenkultra_vod_favorites")

/**
 * Persists the user's favourite movies/series as an ORDERED list — mirrors [FavoritesStore]
 * for live channels. Order matters because the user reorders favourites with the Move (blue)
 * button. Stored as the full [VodItem] JSON (not just ids) so a favourite still shows in the
 * FAVORITES filter even when it isn't part of the currently loaded category/page.
 */
@Singleton
class VodFavoritesStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val ds = context.vodFavoritesDataStore

    val favoritesFlow: Flow<List<VodItem>> = ds.data.map { prefs -> decode(prefs[KEY_FAVORITES]) }

    private suspend fun current(): List<VodItem> = decode(ds.data.map { it[KEY_FAVORITES] }.first())

    private fun decode(json: String?): List<VodItem> =
        json?.let { runCatching { gson.fromJson<List<VodItem>>(it, LIST_TYPE) }.getOrNull() }.orEmpty()

    /** Adds the item if it isn't a favourite yet, or removes it if it already is. */
    suspend fun toggle(item: VodItem) {
        val list = current().toMutableList()
        val existing = list.indexOfFirst { it.id == item.id }
        if (existing >= 0) list.removeAt(existing) else list.add(item)
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

    private suspend fun save(list: List<VodItem>) {
        ds.edit { it[KEY_FAVORITES] = gson.toJson(list) }
    }

    private companion object {
        val KEY_FAVORITES = stringPreferencesKey("favorite_vod")
        val LIST_TYPE = object : TypeToken<List<VodItem>>() {}.type
    }
}
