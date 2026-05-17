package com.eight87.whisperboy.data.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.eight87.whisperboy.data.settings.Setting
import com.eight87.whisperboy.data.settings.setting
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Phase G — narrow facet (R.A + R.B prefig) for the sleep timer's user-tunable knobs.
 *
 * Five independent knobs:
 *  - [defaultDuration] — the duration the bottom-sheet's "default" timer button arms (default 30m).
 *  - [fadeOutDuration] — how long before the timer fires the volume should ramp down (default 30s).
 *  - [shakeToResume] — register the post-fire shake detector? (default true).
 *  - [autoArmWindowStart] / [autoArmWindowEnd] — daily window in which playback start should
 *    auto-arm the timer (G.6 — both null = disabled).
 *
 * Backed by a dedicated `sleep_timer_settings` DataStore-Preferences file in
 * [AndroidSleepTimerSettings]; kept off the `playback_settings` store so a future "reset sleep
 * timer" affordance in K.3 can nuke this file alone (R.B store split pattern).
 *
 * Durations persisted as `Long ms` (DataStore-Preferences has no `Duration` key type); read-side
 * Flows expose `kotlin.time.Duration`. LocalTime persisted as minute-of-day (`0..1439`); `-1`
 * sentinel means "unset".
 */
interface SleepTimerSettings {
    val defaultDuration: Flow<Duration>
    val fadeOutDuration: Flow<Duration>
    val shakeToResume: Flow<Boolean>
    val autoArmWindowStart: Flow<LocalTime?>
    val autoArmWindowEnd: Flow<LocalTime?>

    suspend fun setDefaultDuration(duration: Duration)
    suspend fun setFadeOutDuration(duration: Duration)
    suspend fun setShakeToResume(enabled: Boolean)
    suspend fun setAutoArmWindow(start: LocalTime?, end: LocalTime?)
}

/**
 * DataStore-backed [SleepTimerSettings].
 *
 * R.B.2 migration: [shakeToResume] runs through the [Setting] factory. The two `Duration`
 * knobs and the `autoArmWindow` pair stay hand-rolled because the persisted type (Long ms /
 * minute-of-day) differs from the exposed domain type (`Duration` / nullable `LocalTime`), and
 * the auto-arm setter atomically writes a *pair* of keys — both shapes fall outside the
 * uniform-T contract of `Setting<T>`. Building a `DurationSetting` / `LocalTimePairSetting`
 * helper would add abstraction weight for one and zero further callers respectively; revisit
 * if a second facet needs the same shape.
 */
class AndroidSleepTimerSettings(
    private val dataStore: DataStore<Preferences>,
) : SleepTimerSettings {

    private val shakeToResumeSetting: Setting<Boolean> =
        dataStore.setting(KEY_SHAKE_TO_RESUME, default = DEFAULT_SHAKE_TO_RESUME)

    override val defaultDuration: Flow<Duration> =
        dataStore.data.map { prefs ->
            val ms = prefs[KEY_DEFAULT_DURATION_MS]
            if (ms != null && ms > 0L) ms.milliseconds else DEFAULT_DURATION
        }

    override val fadeOutDuration: Flow<Duration> =
        dataStore.data.map { prefs ->
            val ms = prefs[KEY_FADE_OUT_MS]
            if (ms != null && ms >= 0L) ms.milliseconds else DEFAULT_FADE_OUT
        }

    override val shakeToResume: Flow<Boolean> = shakeToResumeSetting.flow

    override val autoArmWindowStart: Flow<LocalTime?> =
        dataStore.data.map { prefs -> decodeMinuteOfDay(prefs[KEY_AUTO_ARM_START_MIN]) }

    override val autoArmWindowEnd: Flow<LocalTime?> =
        dataStore.data.map { prefs -> decodeMinuteOfDay(prefs[KEY_AUTO_ARM_END_MIN]) }

    override suspend fun setDefaultDuration(duration: Duration) {
        dataStore.edit { it[KEY_DEFAULT_DURATION_MS] = duration.inWholeMilliseconds.coerceAtLeast(0L) }
    }

    override suspend fun setFadeOutDuration(duration: Duration) {
        dataStore.edit { it[KEY_FADE_OUT_MS] = duration.inWholeMilliseconds.coerceAtLeast(0L) }
    }

    override suspend fun setShakeToResume(enabled: Boolean) = shakeToResumeSetting.set(enabled)

    override suspend fun setAutoArmWindow(start: LocalTime?, end: LocalTime?) {
        dataStore.edit {
            it[KEY_AUTO_ARM_START_MIN] = encodeMinuteOfDay(start)
            it[KEY_AUTO_ARM_END_MIN] = encodeMinuteOfDay(end)
        }
    }

    private companion object {
        val KEY_DEFAULT_DURATION_MS = longPreferencesKey("default_duration_ms")
        val KEY_FADE_OUT_MS = longPreferencesKey("fade_out_ms")
        val KEY_SHAKE_TO_RESUME = booleanPreferencesKey("shake_to_resume")
        val KEY_AUTO_ARM_START_MIN = longPreferencesKey("auto_arm_start_minute")
        val KEY_AUTO_ARM_END_MIN = longPreferencesKey("auto_arm_end_minute")

        val DEFAULT_DURATION: Duration = 30.minutes
        val DEFAULT_FADE_OUT: Duration = 30.seconds
        const val DEFAULT_SHAKE_TO_RESUME = true

        const val UNSET_MINUTE = -1L

        fun decodeMinuteOfDay(raw: Long?): LocalTime? {
            if (raw == null || raw < 0L || raw >= 24L * 60L) return null
            val h = (raw / 60L).toInt()
            val m = (raw % 60L).toInt()
            return LocalTime.of(h, m)
        }

        fun encodeMinuteOfDay(time: LocalTime?): Long {
            return if (time == null) UNSET_MINUTE else (time.hour * 60L + time.minute)
        }
    }
}
