package com.eight87.whisperboy.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.eight87.whisperboy.data.settings.EnumSetting
import com.eight87.whisperboy.data.settings.enumSetting
import kotlinx.coroutines.flow.Flow

/**
 * Narrow facet for the library screen's persisted UI preferences (R.A pattern — three
 * `Flow<X>` properties + three `suspend setX(...)` setters, no leaking DataStore).
 *
 * Backed by a `library_ui` DataStore-Preferences file in [AndroidLibraryUiSettings];
 * stored as `enum.name` strings, unknown / null values coerce to defaults.
 *
 * Phase E.3 follow-up: replaces three `mutableStateOf` calls in `LibraryScreen` with
 * Flow-backed state so the user's last selection survives app restart.
 *
 * **R.B.2 reference impl.** This facet is the first end-to-end migration to the
 * [com.eight87.whisperboy.data.settings.Setting] value type. The public interface keeps the
 * `Flow<X>` + `suspend setX(...)` shape (so consumers don't churn); the impl just delegates to
 * an [EnumSetting] per knob. Future facets (`PlaybackSettings`, `ThemeSettings`,
 * `OnboardingSettings`, `LibraryScanFilterSettings`, `SleepTimerSettings`) can follow the same
 * pattern — or expose `val gridMode: EnumSetting<GridMode>` directly if a fresh sub-page is
 * willing to take the dependency.
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
 * DataStore-backed implementation. Each knob is an [EnumSetting] built via the
 * [enumSetting] factory — handles key creation, `enum.name` round-trip, and default coercion
 * in one expression.
 */
class AndroidLibraryUiSettings(
    dataStore: DataStore<Preferences>,
) : LibraryUiSettings {

    private val gridModeSetting: EnumSetting<GridMode> =
        dataStore.enumSetting("grid_mode", GridMode.Grid)
    private val sortKeySetting: EnumSetting<BookSortKey> =
        dataStore.enumSetting("sort_key", BookSortKey.Title)
    private val filterSetting: EnumSetting<BookFilter> =
        dataStore.enumSetting("filter", BookFilter.All)

    override val gridMode: Flow<GridMode> = gridModeSetting.flow
    override val sortKey: Flow<BookSortKey> = sortKeySetting.flow
    override val filter: Flow<BookFilter> = filterSetting.flow

    override suspend fun setGridMode(mode: GridMode) = gridModeSetting.set(mode)
    override suspend fun setSortKey(key: BookSortKey) = sortKeySetting.set(key)
    override suspend fun setFilter(filter: BookFilter) = filterSetting.set(filter)
}
