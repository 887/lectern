package com.eight87.whisperboy.ui.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.whisperboy.R
import com.eight87.whisperboy.data.library.ChapterSource
import com.eight87.whisperboy.playback.NowPlayingState
import com.eight87.whisperboy.playback.PlaybackUiState
import com.eight87.whisperboy.playback.TransportCommands
import com.eight87.whisperboy.ui.common.CoverArt
import kotlinx.coroutines.launch

/**
 * Phase F.1 — full-screen player.
 *
 * Takes only the narrow [NowPlayingState] + [TransportCommands] interfaces (R.A discipline) — no
 * import of `BookSource`, no import of `PlaybackController`. Cold-start-perf discipline: state
 * collected via `collectAsStateWithLifecycle` (B.1); no `BoxWithConstraints` for sizing (B.3);
 * scrubber slider's value-change handler is direct (no `Modifier.offset { IntOffset(...) }`
 * needed since `Slider` already handles position via its own state).
 *
 * Five view branches: Idle (no session — unusual; route always lands with a bookId) shows a
 * spinner; Loading (controller connecting OR book row not yet emitted) shows a spinner;
 * BookNotFound shows an error message + back; Loaded renders the player chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(
    state: NowPlayingState,
    transport: TransportCommands,
    chapterSource: ChapterSource,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by state.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var chapterSheetOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = playerTitle(uiState)) },
            expandedHeight = 32.dp,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.player_back_cd),
                    )
                }
            },
            actions = {
                IconButton(onClick = { /* Phase G */ }) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = stringResource(R.string.player_sleep_timer_cd),
                    )
                }
                IconButton(onClick = { /* Phase H */ }) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = stringResource(R.string.player_bookmark_cd),
                    )
                }
                IconButton(
                    onClick = { chapterSheetOpen = true },
                    enabled = uiState is PlaybackUiState.Loaded,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.player_chapter_list_cd),
                    )
                }
            },
        )

        when (val s = uiState) {
            PlaybackUiState.Idle,
            PlaybackUiState.Loading -> PlayerLoading(modifier = Modifier.weight(1f).fillMaxWidth())
            PlaybackUiState.BookNotFound -> PlayerBookNotFound(modifier = Modifier.weight(1f).fillMaxWidth())
            is PlaybackUiState.Loaded -> PlayerLoaded(
                state = s,
                transport = transport,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }

    // Lazy-mount: the chapter list LazyColumn is only composed while the sheet is open. Per
    // cold-start-perf A.5, sheet-only data (the full chapter list) does NOT ride in
    // PlaybackUiState.Loaded — the sheet fetches via ChapterSource the first time it opens.
    val loaded = uiState as? PlaybackUiState.Loaded
    if (chapterSheetOpen && loaded != null) {
        ChapterListSheet(
            bookId = loaded.book.bookId,
            currentChapterIndex = loaded.currentChapter?.chapterIndex ?: -1,
            chapterSource = chapterSource,
            onChapterTap = { positionInBookMs ->
                scope.launch { transport.seekTo(positionInBookMs) }
                chapterSheetOpen = false
            },
            onDismiss = { chapterSheetOpen = false },
        )
    }
}

@Composable
private fun PlayerLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.player_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayerBookNotFound(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.player_book_not_found_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.player_book_not_found_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlayerLoaded(
    state: PlaybackUiState.Loaded,
    transport: TransportCommands,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val chapterDurationMs = state.currentChapter?.durationMs ?: state.book.durationMs

    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        CoverArt(
            coverPath = state.book.coverPath,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = state.book.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = chapterDisplayTitle(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(24.dp))

        PlayerScrubber(
            positionInChapterMs = positionInChapter(state),
            chapterDurationMs = chapterDurationMs,
            onSeek = { newPositionInChapterMs ->
                val absolute = (state.currentChapter?.positionInBookMs ?: 0L) + newPositionInChapterMs
                scope.launch { transport.seekTo(absolute) }
            },
        )

        Spacer(Modifier.height(16.dp))

        PlayerTransport(
            isPlaying = state.isPlaying,
            onPlayPause = {
                scope.launch { if (state.isPlaying) transport.pause() else transport.play() }
            },
            onRewind = { scope.launch { transport.rewind30() } },
            onForward = { scope.launch { transport.forward30() } },
            onPrev = { scope.launch { transport.prevChapter() } },
            onNext = { scope.launch { transport.nextChapter() } },
        )
    }
}

@Composable
private fun PlayerScrubber(
    positionInChapterMs: Long,
    chapterDurationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val safeDuration = chapterDurationMs.coerceAtLeast(1L)
    val safePosition = positionInChapterMs.coerceIn(0L, safeDuration)
    Slider(
        value = safePosition.toFloat(),
        onValueChange = { onSeek(it.toLong()) },
        valueRange = 0f..safeDuration.toFloat(),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatMmSs(safePosition),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = formatMmSs(safeDuration),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlayerTransport(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.player_skip_prev_cd),
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = onRewind) {
            Icon(
                imageVector = Icons.Filled.Replay30,
                contentDescription = stringResource(R.string.player_rewind_30_cd),
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) R.string.player_pause_cd else R.string.player_play_cd,
                ),
                modifier = Modifier.size(56.dp),
            )
        }
        IconButton(onClick = onForward) {
            Icon(
                imageVector = Icons.Filled.Forward30,
                contentDescription = stringResource(R.string.player_forward_30_cd),
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.player_skip_next_cd),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun playerTitle(state: PlaybackUiState): String = when (state) {
    is PlaybackUiState.Loaded -> state.book.title
    else -> ""
}

@Composable
private fun chapterDisplayTitle(state: PlaybackUiState.Loaded): String {
    val ch = state.currentChapter
    return when {
        ch?.title != null -> ch.title
        ch != null -> stringResource(R.string.player_unknown_chapter, ch.chapterIndex + 1)
        else -> ""
    }
}

private fun positionInChapter(state: PlaybackUiState.Loaded): Long {
    val chapterStart = state.currentChapter?.positionInBookMs ?: 0L
    return (state.positionInBookMs - chapterStart).coerceAtLeast(0L)
}

