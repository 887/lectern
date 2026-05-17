package com.eight87.whisperboy.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
 */
interface LibraryScanFilterSettings {
    /** Lowercase extensions the user has disabled. Empty default = all enabled. */
    val disabledExtensions: Flow<Set<String>>

    suspend fun setDisabledExtensions(extensions: Set<String>)

    /** Synchronous snapshot used by the scanner on the IO dispatcher. */
    suspend fun snapshot(): Set<String>
}

class AndroidLibraryScanFilterSettings(
    private val dataStore: DataStore<Preferences>,
) : LibraryScanFilterSettings {

    override val disabledExtensions: Flow<Set<String>> =
        dataStore.data.map { prefs ->
            (prefs[KEY_DISABLED_EXTENSIONS] ?: emptySet())
                .mapTo(mutableSetOf()) { it.lowercase() }
        }

    override suspend fun setDisabledExtensions(extensions: Set<String>) {
        val normalized = extensions.mapTo(mutableSetOf()) { it.lowercase() }
        dataStore.edit { it[KEY_DISABLED_EXTENSIONS] = normalized }
    }

    override suspend fun snapshot(): Set<String> {
        val prefs = dataStore.data.first()
        return (prefs[KEY_DISABLED_EXTENSIONS] ?: emptySet())
            .mapTo(mutableSetOf()) { it.lowercase() }
    }

    private companion object {
        val KEY_DISABLED_EXTENSIONS = stringSetPreferencesKey("disabled_extensions")
    }
}
