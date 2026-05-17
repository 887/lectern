package com.eight87.whisperboy.data.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Narrow facet (R.A pattern, R.B store split) for the player's user-tunable defaults.
 *
 * Two families of knobs are stored here:
 *
 *  - **Seek defaults** — applied by the transport buttons / auto-rewind on long pause:
 *    - [rewindSeconds] — applied by the rewind transport button (Phase F.3, default 30).
 *    - [forwardSeconds] — applied by the forward transport button (Phase F.3, default 30).
 *    - [autoRewindSeconds] — applied automatically by `PlaybackController.play()` after a long
 *      pause (>5min) so the listener catches their bearings. Voice ships this; Phase F.4 (default 5).
 *    Allowed picker values are 5 / 10 / 30 / 60 — any other persisted value coerces to the default
 *    on read so a corrupt / future-version store can't render a garbage selector state.
 *
 *  - **Per-book defaults** (Phase K.2 / J.4) — copied to [BookEntity.speed] / [BookEntity.skipSilenceEnabled]
 *    / [BookEntity.gainDb] on a book's FIRST scan. After that the per-book row is the source of
 *    truth; editing these globals does NOT retro-write existing rows.
 *    - [defaultSpeed] — playback rate (range 0.5..3.5, default 1.0).
 *    - [defaultSkipSilence] — skip-silence on/off (default false).
 *    - [defaultGainDb] — volume gain in dB (range -3..+12, default 0.0).
 *
 * Backed by a dedicated `playback_settings` DataStore-Preferences file in
 * [AndroidPlaybackSettings]; kept off the `library_ui` store so a "reset playback" affordance in
 * a future settings catalog (Phase K) can nuke this file alone.
 */
interface PlaybackSettings {
    val rewindSeconds: Flow<Int>
    val forwardSeconds: Flow<Int>
    val autoRewindSeconds: Flow<Int>

    val defaultSpeed: Flow<Float>
    val defaultSkipSilence: Flow<Boolean>
    val defaultGainDb: Flow<Float>

    suspend fun setRewindSeconds(seconds: Int)
    suspend fun setForwardSeconds(seconds: Int)
    suspend fun setAutoRewindSeconds(seconds: Int)

    suspend fun setDefaultSpeed(speed: Float)
    suspend fun setDefaultSkipSilence(enabled: Boolean)
    suspend fun setDefaultGainDb(db: Float)
}

/**
 * DataStore-backed [PlaybackSettings]. Mirrors [com.eight87.whisperboy.data.library.AndroidLibraryUiSettings]
 * — typed preference keys + `data.map { ... }` Flows + `edit { ... }` setters, with
 * coerce-to-default on out-of-range reads.
 */
class AndroidPlaybackSettings(
    private val dataStore: DataStore<Preferences>,
) : PlaybackSettings {

    override val rewindSeconds: Flow<Int> =
        dataStore.data.map { prefs -> coerceSeconds(prefs[KEY_REWIND_SECONDS], DEFAULT_SEEK_SECONDS) }

    override val forwardSeconds: Flow<Int> =
        dataStore.data.map { prefs -> coerceSeconds(prefs[KEY_FORWARD_SECONDS], DEFAULT_SEEK_SECONDS) }

    override val autoRewindSeconds: Flow<Int> =
        dataStore.data.map { prefs -> coerceAutoRewindSeconds(prefs[KEY_AUTO_REWIND_SECONDS]) }

    override val defaultSpeed: Flow<Float> =
        dataStore.data.map { prefs -> coerceSpeed(prefs[KEY_DEFAULT_SPEED]) }

    override val defaultSkipSilence: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_DEFAULT_SKIP_SILENCE] ?: DEFAULT_SKIP_SILENCE }

    override val defaultGainDb: Flow<Float> =
        dataStore.data.map { prefs -> coerceGain(prefs[KEY_DEFAULT_GAIN_DB]) }

    override suspend fun setRewindSeconds(seconds: Int) {
        dataStore.edit { it[KEY_REWIND_SECONDS] = sanitizeSeconds(seconds, DEFAULT_SEEK_SECONDS) }
    }

    override suspend fun setForwardSeconds(seconds: Int) {
        dataStore.edit { it[KEY_FORWARD_SECONDS] = sanitizeSeconds(seconds, DEFAULT_SEEK_SECONDS) }
    }

    override suspend fun setAutoRewindSeconds(seconds: Int) {
        dataStore.edit { it[KEY_AUTO_REWIND_SECONDS] = sanitizeAutoRewindSeconds(seconds) }
    }

    override suspend fun setDefaultSpeed(speed: Float) {
        dataStore.edit { it[KEY_DEFAULT_SPEED] = clampSpeed(speed) }
    }

    override suspend fun setDefaultSkipSilence(enabled: Boolean) {
        dataStore.edit { it[KEY_DEFAULT_SKIP_SILENCE] = enabled }
    }

    override suspend fun setDefaultGainDb(db: Float) {
        dataStore.edit { it[KEY_DEFAULT_GAIN_DB] = clampGain(db) }
    }

    private companion object {
        val KEY_REWIND_SECONDS = intPreferencesKey("rewind_seconds")
        val KEY_FORWARD_SECONDS = intPreferencesKey("forward_seconds")
        val KEY_AUTO_REWIND_SECONDS = intPreferencesKey("auto_rewind_seconds")

        val KEY_DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val KEY_DEFAULT_SKIP_SILENCE = booleanPreferencesKey("default_skip_silence")
        val KEY_DEFAULT_GAIN_DB = floatPreferencesKey("default_gain_db")

        const val DEFAULT_SEEK_SECONDS = 30
        const val DEFAULT_AUTO_REWIND_SECONDS = 5

        const val DEFAULT_SPEED = 1.0f
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3.5f

        const val DEFAULT_SKIP_SILENCE = false

        const val DEFAULT_GAIN_DB = 0.0f
        const val MIN_GAIN_DB = -3.0f
        const val MAX_GAIN_DB = 12.0f

        /** Coerce a persisted seek-seconds value; unknown / null / out-of-set → [default]. */
        fun coerceSeconds(raw: Int?, default: Int): Int =
            if (raw != null && raw in SECONDS_ALLOWED) raw else default

        /** Sanitize a seek-seconds setter input the same way. */
        fun sanitizeSeconds(raw: Int, default: Int): Int =
            if (raw in SECONDS_ALLOWED) raw else default

        /** Auto-rewind picker offers 0 (off) / 3 / 5 / 10 per K.2 spec. */
        fun sanitizeAutoRewindSeconds(raw: Int): Int =
            if (raw in AUTO_REWIND_ALLOWED) raw else DEFAULT_AUTO_REWIND_SECONDS

        /** Coerce a persisted auto-rewind value; unknown / null / out-of-set → default 5. */
        fun coerceAutoRewindSeconds(raw: Int?): Int =
            if (raw != null && raw in AUTO_REWIND_ALLOWED) raw else DEFAULT_AUTO_REWIND_SECONDS

        val SECONDS_ALLOWED = setOf(5, 10, 30, 60)

        val AUTO_REWIND_ALLOWED = setOf(0, 3, 5, 10)

        fun coerceSpeed(raw: Float?): Float =
            if (raw != null && raw in MIN_SPEED..MAX_SPEED) raw else DEFAULT_SPEED

        fun clampSpeed(raw: Float): Float = raw.coerceIn(MIN_SPEED, MAX_SPEED)

        fun coerceGain(raw: Float?): Float =
            if (raw != null && raw in MIN_GAIN_DB..MAX_GAIN_DB) raw else DEFAULT_GAIN_DB

        fun clampGain(raw: Float): Float = raw.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
    }
}
