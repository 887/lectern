package com.eight87.whisperboy.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.whisperboy.R
import com.eight87.whisperboy.data.library.BookEntity
import com.eight87.whisperboy.data.library.BookSource
import com.eight87.whisperboy.ui.common.CoverArt

/**
 * R.F.9 — per-author detail screen. Renders a cover grid of every active book by
 * [authorName], filtered on the data layer via [BookSource.observeBooksByAuthor].
 * The composable never filters in Compose; the Flow arrives ready-to-render.
 *
 * Surface ladder honoured: page = `surface` (Scaffold default), empty-state callout =
 * `surfaceContainerHigh` (matches `LibraryEmptyState` in [LibraryScreen]).
 *
 * Entry point: the [LibraryScreen] long-press action sheet's "View all by <author>" row.
 * Tap on a tile fires [onBookTap] — the same `bookCommands.playBook` + open-sheet path
 * the library grid uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorDetailScreen(
    authorName: String,
    bookSource: BookSource,
    onBack: () -> Unit,
    onBookTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val books by bookSource.observeBooksByAuthor(authorName)
        .collectAsStateWithLifecycle(initialValue = emptyList<BookEntity>())

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.author_detail_title_format, authorName),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.author_detail_back_cd),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        if (books.isEmpty()) {
            // Empty state — extremely unlikely (a row only reaches this screen via a long-press
            // on an existing book whose `author` matched), but a rescan between the long-press
            // and arrival here could in principle drop the last row for that author.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.author_detail_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        } else {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                state = gridState,
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 96.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                books.forEach { book ->
                    item(key = "author-detail-book:${book.bookId}") {
                        AuthorDetailTile(
                            book = book,
                            onTap = { onBookTap(book.bookId) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Per-book cover tile — same shape the library cover grid uses (`BookGridTile` private to
 * [LibraryScreen]). No long-press affordance here: the user already came from a long-press
 * on the library, so the sheet path isn't useful on this surface.
 */
@Composable
private fun AuthorDetailTile(
    book: BookEntity,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
    ) {
        CoverArt(
            coverPath = book.coverPath,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
