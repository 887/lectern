package com.eight87.whisperboy.data.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.eight87.whisperboy.data.settings.EnumSetting
import com.eight87.whisperboy.data.settings.Setting
import com.eight87.whisperboy.data.settings.enumSetting
import com.eight87.whisperboy.data.settings.setting
import kotlinx.coroutines.flow.Flow

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
 *
 * R.B.2 migration: both knobs delegate to the [Setting] / [EnumSetting] factories.
 */
class AndroidThemeSettings(
    dataStore: DataStore<Preferences>,
) : ThemeSettings {

    private val modeSetting: EnumSetting<ThemeMode> =
        dataStore.enumSetting("mode", ThemeMode.FollowSystem)
    private val dynamicColorSetting: Setting<Boolean> =
        dataStore.setting(booleanPreferencesKey("dynamic_color"), default = true)

    override val mode: Flow<ThemeMode> = modeSetting.flow
    override val dynamicColor: Flow<Boolean> = dynamicColorSetting.flow

    override suspend fun setMode(mode: ThemeMode) = modeSetting.set(mode)
    override suspend fun setDynamicColor(enabled: Boolean) = dynamicColorSetting.set(enabled)
}
