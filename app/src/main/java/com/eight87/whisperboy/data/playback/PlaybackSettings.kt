package com.eight87.whisperboy.data.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.eight87.whisperboy.data.settings.Setting
import com.eight87.whisperboy.data.settings.setting
import kotlinx.coroutines.flow.Flow

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
 * DataStore-backed [PlaybackSettings].
 *
 * R.B.2 migration: every knob is a [Setting]. Knobs with non-trivial validation
 * (seek-seconds coerce-to-set, speed/gain clamp-to-range) use the
 * `setting(key, decode, encode)` overload so the coercion lives next to the key
 * declaration instead of in hand-rolled `data.map { ... }` Flows.
 */
class AndroidPlaybackSettings(
    dataStore: DataStore<Preferences>,
) : PlaybackSettings {

    private val rewindSetting: Setting<Int> = dataStore.setting(
        key = intPreferencesKey("rewind_seconds"),
        decode = { raw -> if (raw != null && raw in SECONDS_ALLOWED) raw else DEFAULT_SEEK_SECONDS },
        encode = { raw -> if (raw in SECONDS_ALLOWED) raw else DEFAULT_SEEK_SECONDS },
    )

    private val forwardSetting: Setting<Int> = dataStore.setting(
        key = intPreferencesKey("forward_seconds"),
        decode = { raw -> if (raw != null && raw in SECONDS_ALLOWED) raw else DEFAULT_SEEK_SECONDS },
        encode = { raw -> if (raw in SECONDS_ALLOWED) raw else DEFAULT_SEEK_SECONDS },
    )

    private val autoRewindSetting: Setting<Int> = dataStore.setting(
        key = intPreferencesKey("auto_rewind_seconds"),
        decode = { raw -> if (raw != null && raw in AUTO_REWIND_ALLOWED) raw else DEFAULT_AUTO_REWIND_SECONDS },
        encode = { raw -> if (raw in AUTO_REWIND_ALLOWED) raw else DEFAULT_AUTO_REWIND_SECONDS },
    )

    private val defaultSpeedSetting: Setting<Float> = dataStore.setting(
        key = floatPreferencesKey("default_speed"),
        decode = { raw -> if (raw != null && raw in MIN_SPEED..MAX_SPEED) raw else DEFAULT_SPEED },
        encode = { raw -> raw.coerceIn(MIN_SPEED, MAX_SPEED) },
    )

    private val defaultSkipSilenceSetting: Setting<Boolean> = dataStore.setting(
        key = booleanPreferencesKey("default_skip_silence"),
        default = DEFAULT_SKIP_SILENCE,
    )

    private val defaultGainSetting: Setting<Float> = dataStore.setting(
        key = floatPreferencesKey("default_gain_db"),
        decode = { raw -> if (raw != null && raw in MIN_GAIN_DB..MAX_GAIN_DB) raw else DEFAULT_GAIN_DB },
        encode = { raw -> raw.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB) },
    )

    override val rewindSeconds: Flow<Int> = rewindSetting.flow
    override val forwardSeconds: Flow<Int> = forwardSetting.flow
    override val autoRewindSeconds: Flow<Int> = autoRewindSetting.flow
    override val defaultSpeed: Flow<Float> = defaultSpeedSetting.flow
    override val defaultSkipSilence: Flow<Boolean> = defaultSkipSilenceSetting.flow
    override val defaultGainDb: Flow<Float> = defaultGainSetting.flow

    override suspend fun setRewindSeconds(seconds: Int) = rewindSetting.set(seconds)
    override suspend fun setForwardSeconds(seconds: Int) = forwardSetting.set(seconds)
    override suspend fun setAutoRewindSeconds(seconds: Int) = autoRewindSetting.set(seconds)
    override suspend fun setDefaultSpeed(speed: Float) = defaultSpeedSetting.set(speed)
    override suspend fun setDefaultSkipSilence(enabled: Boolean) = defaultSkipSilenceSetting.set(enabled)
    override suspend fun setDefaultGainDb(db: Float) = defaultGainSetting.set(db)

    private companion object {
        const val DEFAULT_SEEK_SECONDS = 30
        const val DEFAULT_AUTO_REWIND_SECONDS = 5

        const val DEFAULT_SPEED = 1.0f
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3.5f

        const val DEFAULT_SKIP_SILENCE = false

        const val DEFAULT_GAIN_DB = 0.0f
        const val MIN_GAIN_DB = -3.0f
        const val MAX_GAIN_DB = 12.0f

        val SECONDS_ALLOWED = setOf(5, 10, 30, 60)
        val AUTO_REWIND_ALLOWED = setOf(0, 3, 5, 10)
    }
}
