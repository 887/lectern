package com.eight87.whisperboy.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.eight87.whisperboy.data.settings.Setting
import com.eight87.whisperboy.data.settings.stringSetSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Phase K.4 sub-screen — persisted scan-filter preferences.
 *
 * Holds the set of audio file extensions the user has DISABLED for scanning.
 * Default = empty set, meaning every extension in [SupportedAudioFormats.extensions]
 * is enabled. Storing the disabled set (rather than the enabled set) means new
 * extensions added to [SupportedAudioFormats.extensions] in future app versions are
 * enabled by default for existing users — the right "fail open" shape for a filter.
 *
 * Backed by its own `scan_filters` DataStore-Preferences file in
 * [AndroidLibraryScanFilterSettings] per R.B store-split; a future "reset scan
 * filters" affordance can `clear()` this file alone.
 *
 * The scanner consumes the disabled set via [SupportedAudioFormats.isAudioFile]'s
 * disabled-extensions parameter, plumbed through [SafLibraryScanner]'s
 * `disabledExtensionsProvider`.
 *
 * R.B.2 migration: the `disabledExtensions` Flow + setter are backed by [Setting]
 * via the [stringSetSetting] factory (lowercases on read AND on write). `snapshot()`
 * remains as a small helper that takes a single emission off the same Flow.
 */
interface LibraryScanFilterSettings {
    /** Lowercase extensions the user has disabled. Empty default = all enabled. */
    val disabledExtensions: Flow<Set<String>>

    suspend fun setDisabledExtensions(extensions: Set<String>)

    /** Synchronous snapshot used by the scanner on the IO dispatcher. */
    suspend fun snapshot(): Set<String>
}

class AndroidLibraryScanFilterSettings(
    dataStore: DataStore<Preferences>,
) : LibraryScanFilterSettings {

    private val setting: Setting<Set<String>> =
        dataStore.stringSetSetting("disabled_extensions", normalise = String::lowercase)

    override val disabledExtensions: Flow<Set<String>> = setting.flow

    override suspend fun setDisabledExtensions(extensions: Set<String>) =
        setting.set(extensions)

    override suspend fun snapshot(): Set<String> = setting.flow.first()
}
