package com.eight87.whisperboy.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Phase P.8 — cheap "did anything change?" probe for each persisted SAF root.
 *
 * Stores `(documentCount, maxMtime)` per tree URI. On every rescan trigger,
 * [AndroidLibraryRescanCoordinator] asks [SafLibraryScanner.computeFingerprint]
 * for the current `(count, maxMtime)` of each root and compares against the
 * stored value via [LibraryFingerprintStore.get]. If unchanged AND the trigger
 * isn't `force = true`, the root is skipped entirely — no SAF walk, no Room
 * write, no cover-extractor work.
 *
 * Why a separate store: this is per-root cache state, not user preferences.
 * Keeping it in its own DataStore file (`library_fingerprints`) means a future
 * "Reset fingerprints" debug action — or a clean re-scan demanded by the user
 * — can wipe this alone without touching `library_roots` / `library_ui` /
 * `playback_settings` / etc. (R.B store-split pattern.)
 */
data class LibraryFingerprint(
    val documentCount: Int,
    val maxMtime: Long,
)

interface LibraryFingerprintStore {

    /** Returns the previously-persisted fingerprint for [treeUri], or `null` if none. */
    suspend fun get(treeUri: String): LibraryFingerprint?

    /** Overwrites the stored fingerprint for [treeUri]. */
    suspend fun set(treeUri: String, fp: LibraryFingerprint)
}

internal class AndroidLibraryFingerprintStore(
    private val dataStore: DataStore<Preferences>,
) : LibraryFingerprintStore {

    override suspend fun get(treeUri: String): LibraryFingerprint? {
        val prefs = dataStore.data.first()
        val count = prefs[intPreferencesKey(countKey(treeUri))] ?: return null
        val mtime = prefs[longPreferencesKey(mtimeKey(treeUri))] ?: return null
        return LibraryFingerprint(count, mtime)
    }

    override suspend fun set(treeUri: String, fp: LibraryFingerprint) {
        dataStore.edit {
            it[intPreferencesKey(countKey(treeUri))] = fp.documentCount
            it[longPreferencesKey(mtimeKey(treeUri))] = fp.maxMtime
        }
    }

    private fun countKey(treeUri: String): String = "$treeUri::doc_count"
    private fun mtimeKey(treeUri: String): String = "$treeUri::max_mtime"
}
