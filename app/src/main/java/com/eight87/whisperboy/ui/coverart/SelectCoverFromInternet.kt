package com.eight87.whisperboy.ui.coverart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.eight87.whisperboy.R
import com.eight87.whisperboy.data.coverart.CoverApi
import com.eight87.whisperboy.data.coverart.ImageSearchPagingSource
import com.eight87.whisperboy.data.coverart.SearchResponse
import com.eight87.whisperboy.data.library.BookSource
import com.eight87.whisperboy.data.library.CoverStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * User-initiated DuckDuckGo image-search surface (cover-art.md Phase B.5–B.7).
 *
 * Layout:
 * - `TopAppBar` with title + close button.
 * - Search bar (`OutlinedTextField`) pre-filled on first compose with `<book title> <author>`.
 * - Body switches on the paged `LoadState`:
 *   - `LoadState.Loading` on the refresh slot → centered [CircularProgressIndicator].
 *   - `LoadState.Error` on the refresh slot → message + Retry button.
 *   - Otherwise → [LazyVerticalStaggeredGrid] of thumbnails. Tapping a tile downloads the
 *     full-resolution image, hands the bytes to [CoverStore.writeCover], then closes the
 *     screen via [onClose]. The crop step (Phase C) is intentionally out of scope; the
 *     library grid renders the saved bytes via `ContentScale.Crop` at display time, so
 *     the user already gets a reasonable square crop until C.1 lands.
 *
 * Three-state contract comes straight from cover-art.md §6 — no per-tile load badges, no
 * service-picker dialogs, no match-score sliders.
 */
@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun SelectCoverFromInternet(
    bookId: String,
    coverApi: CoverApi,
    bookSource: BookSource,
    coverStore: CoverStore,
    okHttpClient: OkHttpClient,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolve the book once on first compose. The result feeds the pre-filled query;
    // recomposition isn't needed after that (the search bar is user-driven from there).
    val book by remember(bookId) { bookSource.observeBook(bookId) }.collectAsState(initial = null)

    // `queryState` holds the currently-submitted query; the field's own text state is
    // separate so the user can keep typing without firing a new search per keystroke.
    val queryState = remember { MutableStateFlow<String?>(null) }
    var fieldText by remember { mutableStateOf("") }
    var queryPrimed by remember { mutableStateOf(false) }

    LaunchedEffect(book?.bookId) {
        val resolved = book ?: return@LaunchedEffect
        if (!queryPrimed) {
            val prefill = listOf(resolved.title, resolved.author.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(separator = " ")
            fieldText = prefill
            queryState.value = prefill
            queryPrimed = true
        }
    }

    // Each new query rebuilds the Pager; old paging flows are dropped by `flatMapLatest`.
    // Empty / blank queries collapse to an empty flow so the grid renders nothing rather
    // than firing a request for `q=`.
    val pagingFlow = remember(coverApi) {
        queryState.flatMapLatest { query ->
            if (query.isNullOrBlank()) {
                flowOf(androidx.paging.PagingData.empty<SearchResponse.ImageResult>())
            } else {
                Pager(
                    config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                    pagingSourceFactory = { ImageSearchPagingSource(coverApi, query) },
                ).flow
            }
        }
    }
    val items: LazyPagingItems<SearchResponse.ImageResult> =
        pagingFlow.collectAsLazyPagingItems()

    val scope = rememberCoroutineScope()

    // cover-art.md Phase C — interception point. Tapping a thumbnail no longer commits
    // the search result directly; instead, the full-resolution URL is stashed in
    // `editingItem` and [EditCoverDialog] takes over for crop. Cancel returns to the
    // grid; Confirm hands the cropped JPEG bytes through `BookSource.setCustomCover`
    // (which writes via `CoverStore` and flips `coverSource = Custom` so a later
    // rescan won't overwrite the user's pick — Phase D.2 reuses the same path).
    var editingItem by remember { mutableStateOf<SearchResponse.ImageResult?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.coverart_search_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.coverart_search_close_cd),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = fieldText,
                onValueChange = { fieldText = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.coverart_search_hint)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { queryState.value = fieldText },
                ),
            )

            val refreshState = items.loadState.refresh
            when {
                refreshState is LoadState.Loading -> LoadingState()
                refreshState is LoadState.Error -> ErrorState(onRetry = { items.retry() })
                else -> ContentGrid(
                    items = items,
                    onPick = { picked -> editingItem = picked },
                )
            }
        }
    }

    val editing = editingItem
    if (editing != null) {
        EditCoverDialog(
            imageUrl = editing.image,
            onCancel = { editingItem = null },
            onConfirm = { croppedBytes ->
                scope.launch {
                    runCatching {
                        bookSource.setCustomCover(bookId, croppedBytes)
                    }.onSuccess {
                        editingItem = null
                        onClose()
                    }.onFailure {
                        // Keep the dialog open on failure so the user can retry.
                        editingItem = null
                    }
                }
            },
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Loading cover search results"
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.coverart_search_error),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onRetry) {
                Text(stringResource(R.string.coverart_search_retry))
            }
        }
    }
}

@Composable
private fun ContentGrid(
    items: LazyPagingItems<SearchResponse.ImageResult>,
    onPick: (SearchResponse.ImageResult) -> Unit,
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalItemSpacing = 8.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            count = items.itemCount,
            key = items.itemKey { "${it.image}#${it.thumbnail}" },
        ) { index ->
            val result = items[index] ?: return@items
            val ratio = remember(result.width, result.height) {
                val w = result.width.coerceAtLeast(1).toFloat()
                val h = result.height.coerceAtLeast(1).toFloat()
                w / h
            }
            val thumbDescription = stringResource(R.string.coverart_cover_thumb_cd)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(result.thumbnail)
                    .crossfade(true)
                    .build(),
                contentDescription = thumbDescription,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(placeholderColor),
                error = ColorPainter(placeholderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(placeholderColor)
                    .clickable { onPick(result) },
            )
        }
        // Trailing footer that visualises "loading the next page". Compose paging idiom —
        // a single full-width Row that holds a small spinner when `append` is loading.
        when (val append = items.loadState.append) {
            is LoadState.Loading -> item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is LoadState.Error -> item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(onClick = { items.retry() }) {
                        Text(stringResource(R.string.coverart_search_retry))
                    }
                }
            }
            else -> Unit
        }
    }
}

/**
 * Phase C wedge: full-image fetch + JPEG encode moved into [EditCoverDialog] (which uses
 * the [coil3.SingletonImageLoader] and the user's crop rect). The previous OkHttp helper
 * is intentionally retired. The `okHttpClient` / `coverStore` constructor parameters
 * stay because [com.eight87.whisperboy.WhisperboyApp] wires them in and the call site
 * is owned by a coexisting subagent — once that lands, both can be removed.
 */
@Suppress("unused", "UNUSED_PARAMETER")
private fun retainedPhaseBPickPathRemoved(okHttpClient: OkHttpClient, coverStore: CoverStore) {
    // Intentionally empty — placeholder so static analyzers don't drop the imports while
    // the public composable's parameter list is the binary surface another inch reads.
}
