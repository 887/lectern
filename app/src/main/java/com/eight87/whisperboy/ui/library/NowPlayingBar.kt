package com.eight87.whisperboy.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.whisperboy.R
import com.eight87.whisperboy.playback.NowPlayingState
import com.eight87.whisperboy.playback.PlaybackUiState
import com.eight87.whisperboy.playback.TransportCommands
import com.eight87.whisperboy.ui.common.CoverArt
import kotlinx.coroutines.launch

/**
 * Phase E.6 — slim now-playing bar pinned to the bottom.
 *
 * Used as the **peek** of [com.eight87.whisperboy.ui.playback.NowPlayingSheet]:
 * tapping anywhere outside the play / pause button calls [onExpand], which the
 * host animates into the full-screen player by driving `sheetProgress` 0 → 1.
 * Vertical drag gestures on the outer Row are forwarded to the host via
 * [onSheetDragDelta] / [onSheetDragSettle], so the user can drag the sheet open
 * with a finger from anywhere on the bar (Auxio-style).
 *
 * Visible only when [PlaybackUiState] is `Loaded`. When idle / not-yet-connected
 * the composable returns immediately so nothing reserves layout space.
 *
 * 2dp thin progress line along the bottom edge tracks `position / duration`
 * across the whole book. `Modifier.graphicsLayer { scaleX = fraction }`
 * (cold-start-perf C.2) defers the state read to draw — the fast-ticking
 * position field doesn't trigger composition each tick.
 */
@Composable
fun NowPlayingBar(
    nowPlayingState: NowPlayingState,
    transport: TransportCommands,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Sheet-drag forwarder. Each vertical drag delta on the outer Row arrives
     * here as a Y-pixel offset (negative = swipe up). Default no-op keeps
     * standalone callers / preview / tests working without the sheet plumbing.
     */
    onSheetDragDelta: (Float) -> Unit = {},
    /** Fired when the drag gesture ends (release or cancel). */
    onSheetDragSettle: () -> Unit = {},
) {
    val state by nowPlayingState.state.collectAsStateWithLifecycle()
    val loaded = state as? PlaybackUiState.Loaded ?: return

    val scope = rememberCoroutineScope()
    val bookDurationMs = loaded.book.durationMs.coerceAtLeast(1L)
    val progressFraction = { (loaded.positionInBookMs.toFloat() / bookDurationMs).coerceIn(0f, 1f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpand() }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { onSheetDragSettle() },
                        onDragCancel = { onSheetDragSettle() },
                    ) { _, delta -> onSheetDragDelta(delta) }
                }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(
                coverPath = loaded.book.coverPath,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = loaded.book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val chapter = loaded.currentChapter
                if (chapter != null) {
                    Text(
                        text = chapter.title
                            ?: stringResource(R.string.player_unknown_chapter, chapter.chapterIndex + 1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                scope.launch {
                    if (loaded.isPlaying) transport.pause() else transport.play()
                }
            }) {
                Icon(
                    imageVector = if (loaded.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (loaded.isPlaying) R.string.player_pause_cd else R.string.player_play_cd,
                    ),
                )
            }
        }

        // Auxio-style thin progress line. `graphicsLayer { scaleX = ... }` (cold-start-perf C.2)
        // defers the fast-ticking position read to draw — keeps the bar off the recomposition
        // hot path. Anchor scale at the left edge via `transformOrigin`.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .graphicsLayer {
                        scaleX = progressFraction()
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
