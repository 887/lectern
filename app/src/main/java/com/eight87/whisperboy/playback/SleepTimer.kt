package com.eight87.whisperboy.playback

import kotlin.time.Duration

/**
 * Phase G.1 — sleep timer arming modes.
 *
 * Sealed type so the timer service dispatches via exhaustive `when` (SOLID O — new modes land as
 * new variants, not as `if (mode.kind == "...")` chains). Voice's `SleepTimerImpl` uses the same
 * shape (`Timed(Duration)` + `EndOfChapter` — `SleepTimerMode` in the `:core:sleeptimer:api`
 * module).
 *
 * `kotlin.time.Duration` is chosen over `Long ms` so the call sites in the bottom sheet read
 * cleanly (`Duration.ofMinutes(15)` instead of `15 * 60 * 1000L`) and the unit is visible at the
 * boundary. The service internally tracks `remainingMs: Long` for tick math.
 */
sealed interface SleepTimerMode {
    data class Timed(val duration: Duration) : SleepTimerMode
    data object EndOfChapter : SleepTimerMode
}

/**
 * Phase G.1 — observable state of the sleep timer.
 *
 * Three states:
 *  - [Inactive] — no timer armed; the button shows a plain icon.
 *  - [Running] — counting down; the button shows the remaining mm:ss and, after the timer crosses
 *    the fade-out window, [fadingOut] becomes true so the UI can render a fade-indicator hint.
 *  - [PausedAwaitingShake] — the timer fired and paused playback; the service is registered with
 *    the accelerometer for a tight window (G.4) so the user can resume by shaking the phone.
 */
sealed interface SleepTimerState {
    data object Inactive : SleepTimerState

    data class Running(
        val mode: SleepTimerMode,
        val remainingMs: Long,
        val fadingOut: Boolean,
    ) : SleepTimerState

    data class PausedAwaitingShake(
        val lastMode: SleepTimerMode,
        val remainingShakeWindowMs: Long,
    ) : SleepTimerState
}
