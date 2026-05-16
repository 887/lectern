package com.eight87.whisperboy.data.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Narrow facet (R.A pattern, R.B store split) for the player's user-tunable seek defaults.
 *
 * Three independent `Int`-seconds knobs:
 *  - [rewindSeconds] — applied by the rewind transport button (Phase F.3, default 30).
 *  - [forwardSeconds] — applied by the forward transport button (Phase F.3, default 30).
 *  - [autoRewindSeconds] — applied automatically by `PlaybackController.play()` after a long
 *    pause (>5min) so the listener catches their bearings. Voice ships this; Phase F.4 (default 5).
 *
 * Allowed picker values are 5 / 10 / 30 / 60 — any other persisted value coerces to the default
 * on read so a corrupt / future-version store can't render a garbage selector state.
 *
 * Backed by a dedicated `playback_settings` DataStore-Preferences file in
 * [AndroidPlaybackSettings]; kept off the `library_ui` store so a "reset playback" affordance in
 * a future settings catalog (Phase K) can nuke this file alone.
 */
interface PlaybackSettings {
    val rewindSeconds: Flow<Int>
    val forwardSeconds: Flow<Int>
    val autoRewindSeconds: Flow<Int>

    suspend fun setRewindSeconds(seconds: Int)
    suspend fun setForwardSeconds(seconds: Int)
    suspend fun setAutoRewindSeconds(seconds: Int)
}

/**
 * DataStore-backed [PlaybackSettings]. Mirrors [com.eight87.whisperboy.data.library.AndroidLibraryUiSettings]
 * — `intPreferencesKey` + `data.map { ... }` Flows + `edit { ... }` setters, with
 * coerce-to-default on out-of-range reads.
 */
class AndroidPlaybackSettings(
    private val dataStore: DataStore<Preferences>,
) : PlaybackSettings {

    override val rewindSeconds: Flow<Int> =
        dataStore.data.map { prefs -> coerce(prefs[KEY_REWIND_SECONDS], DEFAULT_SEEK_SECONDS) }

    override val forwardSeconds: Flow<Int> =
        dataStore.data.map { prefs -> coerce(prefs[KEY_FORWARD_SECONDS], DEFAULT_SEEK_SECONDS) }

    override val autoRewindSeconds: Flow<Int> =
        dataStore.data.map { prefs -> coerce(prefs[KEY_AUTO_REWIND_SECONDS], DEFAULT_AUTO_REWIND_SECONDS) }

    override suspend fun setRewindSeconds(seconds: Int) {
        dataStore.edit { it[KEY_REWIND_SECONDS] = sanitize(seconds, DEFAULT_SEEK_SECONDS) }
    }

    override suspend fun setForwardSeconds(seconds: Int) {
        dataStore.edit { it[KEY_FORWARD_SECONDS] = sanitize(seconds, DEFAULT_SEEK_SECONDS) }
    }

    override suspend fun setAutoRewindSeconds(seconds: Int) {
        dataStore.edit { it[KEY_AUTO_REWIND_SECONDS] = sanitize(seconds, DEFAULT_AUTO_REWIND_SECONDS) }
    }

    private companion object {
        val KEY_REWIND_SECONDS = intPreferencesKey("rewind_seconds")
        val KEY_FORWARD_SECONDS = intPreferencesKey("forward_seconds")
        val KEY_AUTO_REWIND_SECONDS = intPreferencesKey("auto_rewind_seconds")

        const val DEFAULT_SEEK_SECONDS = 30
        const val DEFAULT_AUTO_REWIND_SECONDS = 5

        /** Coerce a persisted value to a known-good value; unknown / null → [default]. */
        fun coerce(raw: Int?, default: Int): Int =
            if (raw != null && raw in ALLOWED_VALUES) raw else default

        /** Sanitize a setter input the same way. Prevents callers persisting garbage. */
        fun sanitize(raw: Int, default: Int): Int =
            if (raw in ALLOWED_VALUES) raw else default

        val ALLOWED_VALUES = setOf(5, 10, 30, 60)
    }
}
