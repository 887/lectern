package com.eight87.whisperboy.data.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Narrow facet for the user's theme preferences (Phase K.5 — mirrors the
 * `LibraryUiSettings` shape: two `Flow<X>` properties + two `suspend setX(...)`
 * setters, no leaking DataStore).
 *
 * Backed by a `theme_settings` DataStore-Preferences file in
 * [AndroidThemeSettings]. Defaults: [ThemeMode.FollowSystem] and
 * `dynamicColor = true`.
 */
interface ThemeSettings {
    val mode: Flow<ThemeMode>
    val dynamicColor: Flow<Boolean>

    suspend fun setMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
}

/**
 * DataStore-backed implementation. Stores [ThemeMode] as `enum.name`;
 * unknown / null values fall back to [ThemeMode.FollowSystem]. Dynamic-color
 * defaults to `true` (matches the pre-K.5 hard-coded behaviour in `Theme.kt`).
 */
class AndroidThemeSettings(
    private val dataStore: DataStore<Preferences>,
) : ThemeSettings {

    override val mode: Flow<ThemeMode> =
        dataStore.data.map { prefs ->
            prefs[KEY_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.FollowSystem
        }

    override val dynamicColor: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_DYNAMIC_COLOR] ?: true }

    override suspend fun setMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    private companion object {
        val KEY_MODE = stringPreferencesKey("mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
