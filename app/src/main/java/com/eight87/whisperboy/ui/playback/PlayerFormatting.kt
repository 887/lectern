package com.eight87.whisperboy.ui.playback

/**
 * mm:ss formatter shared between [PlaybackScreen] and [ChapterListSheet].
 *
 * Single-source-of-truth so the chapter sheet's duration column and the scrubber's timestamp row
 * stay visually identical. Plain non-composable Kotlin — no Compose deps, easy to unit-test if we
 * ever want to.
 */
internal fun formatMmSs(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
