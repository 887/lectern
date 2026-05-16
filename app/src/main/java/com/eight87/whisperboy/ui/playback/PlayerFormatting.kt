package com.eight87.whisperboy.ui.playback

/**
 * mm:ss formatter shared between the [PlaybackScreen] scrubber row and the inline chapter queue's
 * per-row duration column.
 *
 * Single-source-of-truth so both surfaces stay visually identical. Plain non-composable Kotlin —
 * no Compose deps, easy to unit-test if we ever want to.
 */
internal fun formatMmSs(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
