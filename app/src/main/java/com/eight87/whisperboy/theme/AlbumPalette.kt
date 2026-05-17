package com.eight87.whisperboy.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Per-book album-art palette — published at the app root by
 * [WhisperboyTheme] so every chrome surface (library, settings,
 * bookmark list, player) can subtly shift toward the currently-playing
 * book's dominant cover color.
 *
 * Ported from tonearmboy's `AlbumPalette` (Theme.kt:50-120 area) —
 * same shape (one `Color?` slot for the surface-blend tint), but
 * whisperboy reads the playing book's cover path off [com.eight87.whisperboy.playback.NowPlayingState]
 * (an audiobook session has at most one playing book) rather than
 * Media3's `Player.currentMediaItem` (tonearmboy's queue model).
 *
 * `null` `surfaceTint` = "no tint applied, surfaces stay at their
 * base scheme values" — same default as tonearmboy's
 * `AlbumPalette.Empty`.
 */
data class AlbumPalette(val surfaceTint: Color?) {
  companion object {
    val Empty: AlbumPalette = AlbumPalette(surfaceTint = null)
  }
}

/**
 * CompositionLocal carrying the currently-playing book's
 * [AlbumPalette]. Default [AlbumPalette.Empty] (no tint applied) —
 * lets any composable preview / unit-render without setting up the
 * whole playback graph.
 *
 * Lives as a `staticCompositionLocalOf` (rather than dynamic) because
 * the value changes only when the playing book switches (rare,
 * coarse-grained) and is read in many leaf places — the static
 * variant skips per-read invalidation tracking, which matches the
 * "rarely changes, read in many places" shape.
 */
val LocalAlbumPalette = staticCompositionLocalOf { AlbumPalette.Empty }
