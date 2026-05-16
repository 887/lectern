package com.eight87.whisperboy.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.whisperboy.R
import com.eight87.whisperboy.data.library.ChapterEntity
import com.eight87.whisperboy.data.library.ChapterSource

/**
 * Phase F.5 — chapter list bottom sheet.
 *
 * Lazy-mount discipline (cold-start-perf A.5): the sheet's `LazyColumn` and its observed flow only
 * exist while the sheet is open. The call site guards mounting with `if (chapterSheetOpen) { ... }`,
 * so the full chapter list never enters the hot-path [com.eight87.whisperboy.playback.PlaybackUiState]
 * projection — this composable fetches chapters via [ChapterSource] the first time it composes.
 *
 * R.A discipline: takes the narrow [ChapterSource] interface (two methods — `observeChaptersForBook`
 * + `chaptersFor`) rather than the full `LibraryRepository`.
 *
 * @param bookId book whose chapter list to render
 * @param currentChapterIndex zero-based index of the active chapter; -1 if none (e.g. no chapters
 *   yet, or chapterless book). The active row gets a `secondaryContainer` background + a filled
 *   play-arrow leading icon.
 * @param chapterSource read-only chapter access
 * @param onChapterTap invoked with the chapter's `positionInBookMs` (cumulative offset within the
 *   book) when a row is tapped. Caller is responsible for seeking + dismissing.
 * @param onDismiss invoked when the sheet is swiped down or the scrim is tapped
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterListSheet(
    bookId: String,
    currentChapterIndex: Int,
    chapterSource: ChapterSource,
    onChapterTap: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // remember() pins the Flow instance to (chapterSource, bookId); collectAsStateWithLifecycle
    // gives us the loaded list once Room emits, empty list until then. Long chapter lists do not
    // recompose the sheet on every position tick — the Flow only changes when chapter rows do.
    val chaptersFlow = remember(chapterSource, bookId) {
        chapterSource.observeChaptersForBook(bookId)
    }
    val chapters by chaptersFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.player_chapter_list_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        val listState = rememberLazyListState()
        // Scroll to the active chapter the first time the list arrives, so users on chapter 47
        // don't have to scroll manually to see where they are.
        LaunchedEffect(chapters.size, currentChapterIndex) {
            if (currentChapterIndex in chapters.indices) {
                listState.scrollToItem(currentChapterIndex)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
        ) {
            items(
                items = chapters,
                key = { it.chapterId },
            ) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    isActive = chapter.chapterIndex == currentChapterIndex,
                    onTap = { onChapterTap(chapter.positionInBookMs) },
                )
            }
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: ChapterEntity,
    isActive: Boolean,
    onTap: () -> Unit,
) {
    val bg = if (isActive) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val fg = if (isActive) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subFg = if (isActive) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onTap)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isActive) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = fg,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = chapter.title
                ?: stringResource(R.string.player_unknown_chapter, chapter.chapterIndex + 1),
            style = MaterialTheme.typography.bodyLarge,
            color = fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatMmSs(chapter.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = subFg,
        )
    }
}
