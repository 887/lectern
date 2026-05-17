package com.eight87.whisperboy.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.eight87.whisperboy.data.library.BookmarkSource
import com.eight87.whisperboy.data.playback.SleepTimerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.time.Duration

/**
 * Phase G — sleep timer service.
 *
 * Owns:
 *  - the active [SleepTimerState], exposed as a [StateFlow] for the player UI.
 *  - the 200ms tick coroutine that decrements `remainingMs` and ramps the player's `volume` via
 *    a `FastOutSlowInInterpolator`-equivalent cubic-bezier curve once the remaining time crosses
 *    the user-configured fade-out window (default 30s; see [SleepTimerSettings.fadeOutDuration]).
 *  - the chapter-boundary collector for [SleepTimerMode.EndOfChapter]: subscribes to the
 *    [PlayerHandle.mediaItemTransitions] flow and pauses on the next emission.
 *  - the auto-bookmark write at fire time, via [BookmarkSource.addBookmark] with
 *    `setBySleepTimer = true`.
 *  - the post-fire [ShakeDetector] registration window (default 30s) — on shake, re-arms the
 *    timer with the last-used mode.
 *
 * SOLID:
 *  - **S** — one reason to change (the sleep-timer behaviour). Volume / pause / position reads
 *    flow through [PlayerHandle], not a fat `PlaybackController` reference.
 *  - **D** — depends on [PlayerHandle] / [BookmarkSource] / [SleepTimerSettings] abstractions;
 *    the concrete `MediaController` stays inside `PlaybackController`.
 *  - **I** — narrow [SleepTimerCommands] surface; the UI never sees this class.
 *
 * Threading: tick coroutine launches on the provided [applicationScope]; the writes back to the
 * player go through `playerHandle.setVolumeNow / pauseNow`, which marshal to Main inside
 * `PlaybackController.onMain`.
 *
 * Voice's `SleepTimerImpl` (`core/sleeptimer/impl/SleepTimerImpl.kt`) is the prior-art shape.
 * Whisperboy's variant uses `StateFlow<SleepTimerState>` (Voice splits into multiple StateFlows)
 * because a single sealed state is easier for the player chrome to consume in one
 * `collectAsStateWithLifecycle`.
 */
internal class AndroidSleepTimer(
    context: Context,
    private val playerHandle: PlayerHandle,
    private val bookmarkSource: BookmarkSource,
    private val sleepTimerSettings: SleepTimerSettings,
    private val applicationScope: CoroutineScope,
) : SleepTimerCommands {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Inactive)
    override val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var endOfChapterJob: Job? = null
    private var shakeWindowJob: Job? = null

    /** The last [SleepTimerMode] the user picked. Re-armed by [ShakeDetector] on shake. */
    private var lastMode: SleepTimerMode? = null

    private val sensorManager: SensorManager? =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var shakeDetector: ShakeDetector? = null

    override suspend fun arm(mode: SleepTimerMode) {
        cancelInternal(restoreVolume = true)
        lastMode = mode
        when (mode) {
            is SleepTimerMode.Timed -> armTimed(mode)
            SleepTimerMode.EndOfChapter -> armEndOfChapter()
        }
    }

    override suspend fun cancel() {
        cancelInternal(restoreVolume = true)
        _state.value = SleepTimerState.Inactive
    }

    /** Internal cancel — does NOT touch `lastMode` (so shake-resume after a self-fire still works). */
    private suspend fun cancelInternal(restoreVolume: Boolean) {
        timerJob?.cancel()
        timerJob = null
        endOfChapterJob?.cancel()
        endOfChapterJob = null
        shakeWindowJob?.cancel()
        shakeWindowJob = null
        unregisterShakeDetector()
        if (restoreVolume) {
            // Always restore to 1.0 so the next session doesn't start muted.
            playerHandle.setVolumeNow(1f)
        }
    }

    // ---------------------------------------------------------------- Timed

    private fun armTimed(mode: SleepTimerMode.Timed) {
        val totalMs = mode.duration.inWholeMilliseconds.coerceAtLeast(MIN_TIMER_MS)
        _state.value = SleepTimerState.Running(mode = mode, remainingMs = totalMs, fadingOut = false)
        timerJob = applicationScope.launch {
            val fadeOutMs = sleepTimerSettings.fadeOutDuration.first().inWholeMilliseconds
                .coerceAtLeast(0L)
            var remaining = totalMs
            while (remaining > 0L) {
                delay(TICK_INTERVAL_MS)
                remaining -= TICK_INTERVAL_MS
                val fading = remaining in 1..fadeOutMs
                if (fading && fadeOutMs > 0L) {
                    // Cubic-ease: t in [0, 1] from "fade just started" to "about to hit zero".
                    val t = ((fadeOutMs - remaining).toDouble() / fadeOutMs).coerceIn(0.0, 1.0)
                    val volume = fastOutSlowIn1MinusT(t).toFloat().coerceIn(0f, 1f)
                    playerHandle.setVolumeNow(volume)
                }
                _state.value = SleepTimerState.Running(
                    mode = mode,
                    remainingMs = remaining.coerceAtLeast(0L),
                    fadingOut = fading,
                )
            }
            fire()
        }
    }

    /**
     * `FastOutSlowInInterpolator`-equivalent (cubic-bezier(0.4, 0.0, 0.2, 1.0)) for the
     * tail-end volume ramp. We want `1.0 → 0.0` over the fade window, so we use `1 - bezier(t)`
     * where `t` runs from 0 (fade start) to 1 (fade end). The bezier is approximated with the
     * standard cubic `t² (3 - 2t)` ease (close enough for an audio ramp; the human ear isn't
     * precise enough at this slope to distinguish the two over 30s, and we avoid pulling in
     * `androidx.interpolator` for one curve).
     */
    private fun fastOutSlowIn1MinusT(t: Double): Double {
        val eased = t * t * (3.0 - 2.0 * t)
        return 1.0 - eased
    }

    // ---------------------------------------------------------------- EndOfChapter

    private fun armEndOfChapter() {
        _state.value = SleepTimerState.Running(
            mode = SleepTimerMode.EndOfChapter,
            // remainingMs is unknown until the chapter ends; UI can render "End of chapter".
            remainingMs = -1L,
            fadingOut = false,
        )
        endOfChapterJob = applicationScope.launch {
            // Pause on the very next chapter transition.
            playerHandle.mediaItemTransitions.first()
            fire()
        }
    }

    // ---------------------------------------------------------------- fire + auto-bookmark + shake

    /**
     * Timer fired. Pause the player, write an auto-bookmark, transition to
     * [SleepTimerState.PausedAwaitingShake], register the [ShakeDetector] for the shake-resume
     * window.
     */
    private suspend fun fire() {
        val mode = lastMode ?: return
        // Snapshot for the bookmark BEFORE pausing (position is identical either way, but symbolic).
        val bookId = playerHandle.currentBookId()
        val chapterId = playerHandle.currentChapterId()
        val positionMs = playerHandle.currentPositionMs()
        playerHandle.pauseNow()
        // Volume restore *after* pause so the user's next manual play() isn't muted.
        playerHandle.setVolumeNow(1f)

        if (bookId != null) {
            // G.5 — auto-bookmark, distinguishable from manual via setBySleepTimer = true.
            // Title null; the bookmark list will format "Sleep timer" from the flag.
            runCatching {
                bookmarkSource.addBookmark(
                    bookId = bookId,
                    chapterId = chapterId,
                    title = null,
                    positionInBookMs = positionMs,
                    setBySleepTimer = true,
                )
            }
        }

        val shakeEnabled = runCatching { sleepTimerSettings.shakeToResume.first() }.getOrDefault(true)
        if (shakeEnabled && accelerometer != null) {
            registerShakeDetector(mode)
            _state.value = SleepTimerState.PausedAwaitingShake(
                lastMode = mode,
                remainingShakeWindowMs = SHAKE_WINDOW_MS,
            )
            shakeWindowJob = applicationScope.launch {
                var remaining = SHAKE_WINDOW_MS
                while (remaining > 0L) {
                    delay(TICK_INTERVAL_MS)
                    remaining -= TICK_INTERVAL_MS
                    val st = _state.value
                    if (st !is SleepTimerState.PausedAwaitingShake) return@launch
                    _state.value = st.copy(remainingShakeWindowMs = remaining.coerceAtLeast(0L))
                }
                // Window expired; tear down.
                unregisterShakeDetector()
                _state.value = SleepTimerState.Inactive
            }
        } else {
            _state.value = SleepTimerState.Inactive
        }
    }

    // ---------------------------------------------------------------- Shake detection

    private fun registerShakeDetector(modeToResume: SleepTimerMode) {
        unregisterShakeDetector()
        val sm = sensorManager ?: return
        val sensor = accelerometer ?: return
        val detector = ShakeDetector(threshold = SHAKE_THRESHOLD_M_PER_S2) {
            // On shake: cancel post-fire state + re-arm with the last mode.
            applicationScope.launch {
                cancelInternal(restoreVolume = true)
                arm(modeToResume)
                // For Timed, the player was paused by `fire()`; re-arming doesn't auto-play.
                // The user shake intent implies "keep playing", so we don't auto-resume here
                // because the playback session lives in `PlaybackController.play()` and we
                // intentionally don't widen `PlayerHandle` to a transport surface for the timer.
                // (Follow-up: a `playNow()` on PlayerHandle if user feedback says shake-resume
                // should also resume playback. For now arming-only matches Voice's split.)
            }
        }
        shakeDetector = detector
        sm.registerListener(detector, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun unregisterShakeDetector() {
        val sm = sensorManager
        val detector = shakeDetector
        if (sm != null && detector != null) sm.unregisterListener(detector)
        shakeDetector = null
    }

    private companion object {
        /** 200ms tick — fine enough to render mm:ss + drive the fade curve smoothly. */
        const val TICK_INTERVAL_MS = 200L

        /** Anything less than ~1s isn't a useful timer; coerce up. */
        const val MIN_TIMER_MS = 1_000L

        /** Post-fire shake window (G.4). Voice uses 30s; tight to avoid put-the-phone-down false positives. */
        const val SHAKE_WINDOW_MS = 30_000L

        /**
         * Shake threshold in m/s² of the vector magnitude (sqrt(x² + y² + z²)). Voice uses 15;
         * empirically gravity alone is ~9.8, so 15 is a clear "shake harder than picking it up"
         * boundary. Calibration on a real device may want to tune; the AVD's emulated sensor
         * pipeline is noisy enough that real-device verification is recommended (see CLAUDE.md).
         */
        const val SHAKE_THRESHOLD_M_PER_S2 = 15.0f
    }
}

/**
 * Minimal accelerometer-driven shake detector. Fires [onShake] when the vector magnitude exceeds
 * [threshold] m/s² (gravity included — gravity alone is ~9.8 m/s², so picking up the phone
 * doesn't trigger).
 *
 * No debouncing beyond the natural sensor sample rate; the call site in [AndroidSleepTimer]
 * unregisters the listener as soon as the first shake fires.
 */
private class ShakeDetector(
    private val threshold: Float,
    private val onShake: () -> Unit,
) : SensorEventListener {

    @Volatile private var fired = false

    override fun onSensorChanged(event: SensorEvent?) {
        if (fired) return
        val v = event?.values ?: return
        if (v.size < 3) return
        val magnitude = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (magnitude >= threshold) {
            fired = true
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
