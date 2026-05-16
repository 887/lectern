package com.eight87.whisperboy.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Narrow facet for the library screen's persisted UI preferences (R.A pattern — three
 * `Flow<X>` properties + three `suspend setX(...)` setters, no leaking DataStore).
 *
 * Backed by a `library_ui` DataStore-Preferences file in [AndroidLibraryUiSettings];
 * stored as `enum.name` strings, unknown / null values coerce to defaults.
 *
 * Phase E.3 follow-up: replaces three `mutableStateOf` calls in `LibraryScreen` with
 * Flow-backed state so the user's last selection survives app restart.
 */
interface LibraryUiSettings {
    val gridMode: Flow<GridMode>
    val sortKey: Flow<BookSortKey>
    val filter: Flow<BookFilter>

    suspend fun setGridMode(mode: GridMode)
    suspend fun setSortKey(key: BookSortKey)
    suspend fun setFilter(filter: BookFilter)
}

/**
 * DataStore-backed implementation. Mirrors the shape of [AndroidPersistedUriPermissionStore]:
 * companion-object keys, `dataStore.data.map { ... }` Flows, `dataStore.edit { ... }` setters.
 */
class AndroidLibraryUiSettings(
    private val dataStore: DataStore<Preferences>,
) : LibraryUiSettings {

    override val gridMode: Flow<GridMode> =
        dataStore.data.map { prefs -> decode(prefs[KEY_GRID_MODE], GridMode.Grid) }

    override val sortKey: Flow<BookSortKey> =
        dataStore.data.map { prefs -> decode(prefs[KEY_SORT_KEY], BookSortKey.Title) }

    override val filter: Flow<BookFilter> =
        dataStore.data.map { prefs -> decode(prefs[KEY_FILTER], BookFilter.All) }

    override suspend fun setGridMode(mode: GridMode) {
        dataStore.edit { it[KEY_GRID_MODE] = mode.name }
    }

    override suspend fun setSortKey(key: BookSortKey) {
        dataStore.edit { it[KEY_SORT_KEY] = key.name }
    }

    override suspend fun setFilter(filter: BookFilter) {
        dataStore.edit { it[KEY_FILTER] = filter.name }
    }

    private companion object {
        val KEY_GRID_MODE = stringPreferencesKey("grid_mode")
        val KEY_SORT_KEY = stringPreferencesKey("sort_key")
        val KEY_FILTER = stringPreferencesKey("filter")
    }
}

/** Decode an `enum.name` string back to its enum, falling back to [default] on null / unknown. */
private inline fun <reified E : Enum<E>> decode(raw: String?, default: E): E =
    raw?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
