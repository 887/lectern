package com.eight87.whisperboy.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.whisperboy.playback.NowPlayingState
import com.eight87.whisperboy.playback.PlaybackUiState

/**
 * Observe [nowPlayingState] for the currently-playing book's
 * `coverPath`, extract its dominant palette color off-main (via
 * [extractTint]), and emit a fresh [AlbumPalette] state.
 *
 * Used by [WhisperboyTheme] to compute the per-book tint that blends
 * into the app-wide surface ladder (library, settings, bookmark
 * screens, mini-player). Tonearmboy ships the same pattern via
 * `AlbumPaletteSource` + `LocalAlbumPalette`; whisperboy's audio
 * model is simpler (one playing book at a time, no queue), so the
 * observer is in-Compose + keyed by `coverPath` rather than going
 * through a dedicated `Source` class.
 *
 * Falls back to [AlbumPalette.Empty] when:
 *  - The session is `Idle` / `Loading` / `BookNotFound`.
 *  - The currently-playing book has no `coverPath`.
 *  - The cover file is missing / unreadable.
 *
 * Takes [NowPlayingState] (not the concrete `PlaybackController`) per
 * R.A — this observer needs only to *read* the active book; it never
 * issues transport.
 */
@Composable
fun rememberPlayingBookPalette(nowPlayingState: NowPlayingState): AlbumPalette {
  val state by nowPlayingState.state.collectAsStateWithLifecycle()
  val coverPath = (state as? PlaybackUiState.Loaded)?.book?.coverPath
  var palette by remember { mutableStateOf(AlbumPalette.Empty) }
  LaunchedEffect(coverPath) {
    palette = AlbumPalette(surfaceTint = extractTint(coverPath))
  }
  return palette
}
