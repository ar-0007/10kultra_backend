package com.tenkultra.tv.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tenkultra.tv.domain.model.Category
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.contentCacheDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "tenkultra_content_cache")

/**
 * Persists the small, slow-changing lists (TV genres, VOD categories) so they show
 * INSTANTLY when the app is reopened, while a fresh copy loads in the background.
 * Keeps reopen snappy instead of waiting on a re-handshake + re-fetch every time.
 */
@Singleton
class ContentCache @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson
) {
    private val ds = context.contentCacheDataStore

    suspend fun saveTvGenres(list: List<Category>) = save(KEY_TV, list)
    suspend fun saveVodCategories(list: List<Category>) = save(KEY_VOD, list)

    suspend fun getTvGenres(): List<Category> = read(KEY_TV)
    suspend fun getVodCategories(): List<Category> = read(KEY_VOD)

    /** Forgets the cached genre/category lists — call on a portal switch so the OLD portal's
     *  lists don't flash on Home before the NEW portal's lists load. */
    suspend fun clear() {
        ds.edit { it.remove(KEY_TV); it.remove(KEY_VOD) }
    }

    private suspend fun save(key: Preferences.Key<String>, list: List<Category>) {
        if (list.isEmpty()) return
        ds.edit { it[key] = gson.toJson(list) }
    }

    private suspend fun read(key: Preferences.Key<String>): List<Category> {
        val json = ds.data.map { it[key] }.first() ?: return emptyList()
        return runCatching { gson.fromJson(json, Array<Category>::class.java).toList() }
            .getOrDefault(emptyList())
    }

    private companion object {
        val KEY_TV = stringPreferencesKey("tv_genres")
        val KEY_VOD = stringPreferencesKey("vod_categories")
    }
}
