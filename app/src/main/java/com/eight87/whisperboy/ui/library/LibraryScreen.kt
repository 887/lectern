package com.eight87.whisperboy.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.eight87.whisperboy.data.library.BookEntity
import com.eight87.whisperboy.data.library.BookFilter
import com.eight87.whisperboy.data.library.BookSortKey
import com.eight87.whisperboy.data.library.BookSource
import com.eight87.whisperboy.data.library.GridMode
import com.eight87.whisperboy.data.library.LibraryRescanCoordinator
import com.eight87.whisperboy.data.library.LibraryRoot
import com.eight87.whisperboy.data.library.LibraryUiSettings
import com.eight87.whisperboy.data.library.PersistedUriPermissionStore
import com.eight87.whisperboy.data.library.RescanState
import com.eight87.whisperboy.ui.common.CoverArt
import com.eight87.whisperboy.ui.common.FastScrollbar
import kotlinx.coroutines.launch

/**
 * Phase E.1 — the library screen.
 *
 * Takes only narrow data interfaces (R.A discipline): [BookSource] for the catalog flow,
 * [PersistedUriPermissionStore] + [LibraryRescanCoordinator] for the overflow-menu folder
 * management (interim home until Phase K's settings tree lands).
 *
 * Top app bar at `expandedHeight = 32dp` (m3-expressive gotcha #6, validated in tonearmboy
 * across 11 screens). Cover grid wrapped in [FastScrollbar] — section-letter chips along the
 * right edge appear when scrolling/dragging, 800ms linger fade-out (tonearmboy `4e2ff73`).
 *
 * Filter chips (E.2), sort menu + sort-aware section keys (E.3), search (E.4), long-press
 * action sheet + multi-select (E.5), and now-playing bar (E.6) land in follow-up commits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    bookSource: BookSource,
    persistedUriPermissionStore: PersistedUriPermissionStore,
    libraryRescanCoordinator: LibraryRescanCoordinator,
    libraryUiSettings: LibraryUiSettings,
    modifier: Modifier = Modifier,
) {
    val books by bookSource.observeBooks()
        .collectAsStateWithLifecycle(initialValue = emptyList<BookEntity>())
    val rescanState by libraryRescanCoordinator.state.collectAsStateWithLifecycle()
    val roots by persistedUriPermissionStore.observeRoots()
        .collectAsStateWithLifecycle(initialValue = emptyList<LibraryRoot>())
    val coroutineScope = rememberCoroutineScope()

    // Phase E.3 follow-up — persisted via DataStore (`library_ui`). `collectAsStateWithLifecycle`
    // (cold-start-perf.md B.1) defers collection to Lifecycle.STARTED so the first emission
    // doesn't fire during composition. Initial values match the defaults the impl coerces to,
    // so the screen renders consistently before the Flow's first emission lands.
    val gridMode by libraryUiSettings.gridMode
        .collectAsStateWithLifecycle(initialValue = GridMode.Grid)
    val sortKey by libraryUiSettings.sortKey
        .collectAsStateWithLifecycle(initialValue = BookSortKey.Title)
    val filter by libraryUiSettings.filter
        .collectAsStateWithLifecycle(initialValue = BookFilter.All)
    var sortMenuOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var manageFoldersOpen by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val filteredBooks = remember(books, filter) { filterBooks(books, filter) }
    val sortedBooks = remember(filteredBooks, sortKey) { sortedBooks(filteredBooks, sortKey) }
    val sectionStarts = remember(sortedBooks, sortKey) { sectionStartsFor(sortedBooks, sortKey) }

    val pickFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) pendingUri = uri }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.library_title)) },
            expandedHeight = 32.dp,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            actions = {
                Box {
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.library_sort_cd),
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                    ) {
                        BookSortKey.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(sortKeyLabel(option))) },
                                leadingIcon = if (option == sortKey) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                        )
                                    }
                                } else null,
                                onClick = {
                                    coroutineScope.launch { libraryUiSettings.setSortKey(option) }
                                    sortMenuOpen = false
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = {
                    val next = if (gridMode == GridMode.Grid) GridMode.List else GridMode.Grid
                    coroutineScope.launch { libraryUiSettings.setGridMode(next) }
                }) {
                    val (icon, cd) = when (gridMode) {
                        GridMode.Grid -> Icons.AutoMirrored.Filled.ViewList to
                            R.string.library_grid_mode_toggle_to_list_cd
                        GridMode.List -> Icons.Filled.GridView to
                            R.string.library_grid_mode_toggle_to_grid_cd
                    }
                    Icon(imageVector = icon, contentDescription = stringResource(cd))
                }
                Box {
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.library_overflow_cd),
                        )
                    }
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_overflow_rescan)) },
                            enabled = rescanState !is RescanState.Running,
                            onClick = {
                                libraryRescanCoordinator.requestRescan()
                                overflowOpen = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_overflow_add_folder)) },
                            onClick = {
                                pickFolder.launch(null)
                                overflowOpen = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_overflow_manage_folders)) },
                            onClick = {
                                manageFoldersOpen = true
                                overflowOpen = false
                            },
                        )
                    }
                }
            },
        )

        if (books.isNotEmpty()) {
            LibraryFilterChips(
                filter = filter,
                onFilterChange = { next ->
                    coroutineScope.launch { libraryUiSettings.setFilter(next) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (sortedBooks.isEmpty()) {
            LibraryEmptyState(modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            when (gridMode) {
                GridMode.Grid -> LibraryCoverGrid(
                    books = sortedBooks,
                    sectionStarts = sectionStarts,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                GridMode.List -> LibraryCoverList(
                    books = sortedBooks,
                    sectionStarts = sectionStarts,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }

    val pending = pendingUri
    if (pending != null) {
        ManageFoldersFolderTypeSheet(
            onDismiss = { pendingUri = null },
            onSelect = { folderType ->
                coroutineScope.launch {
                    persistedUriPermissionStore.addRoot(pending, folderType)
                    pendingUri = null
                }
            },
        )
    }

    if (manageFoldersOpen) {
        ManageFoldersSheet(
            roots = roots,
            onRemove = { uri ->
                coroutineScope.launch { persistedUriPermissionStore.removeRoot(uri) }
            },
            onDismiss = { manageFoldersOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryFilterChips(
    filter: BookFilter,
    onFilterChange: (BookFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookFilter.entries.forEach { option ->
            FilterChip(
                selected = filter == option,
                onClick = { onFilterChange(option) },
                label = { Text(stringResource(filterLabel(option))) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun LibraryEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.library_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LibraryCoverGrid(
    books: List<BookEntity>,
    sectionStarts: List<Pair<Int, String>>,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    Box(modifier = modifier) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            state = gridState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items = books, key = { "book:${it.bookId}" }) { book ->
                BookGridTile(book)
            }
        }

        FastScrollbar(
            state = gridState,
            sectionStarts = sectionStarts,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun LibraryCoverList(
    books: List<BookEntity>,
    sectionStarts: List<Pair<Int, String>>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items = books, key = { "book:${it.bookId}" }) { book ->
                BookListRow(book)
            }
        }

        FastScrollbar(
            state = listState,
            sectionStarts = sectionStarts,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun BookGridTile(book: BookEntity) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
        Text(
            text = book.author ?: stringResource(R.string.library_book_unknown_author),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BookListRow(book: BookEntity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            coverPath = book.coverPath,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.author ?: stringResource(R.string.library_book_unknown_author),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun sortKeyLabel(key: BookSortKey): Int = when (key) {
    BookSortKey.Recent -> R.string.library_sort_recent
    BookSortKey.Title -> R.string.library_sort_title
    BookSortKey.Author -> R.string.library_sort_author
}

private fun filterLabel(filter: BookFilter): Int = when (filter) {
    BookFilter.All -> R.string.library_filter_all
    BookFilter.Current -> R.string.library_filter_current
    BookFilter.NotStarted -> R.string.library_filter_not_started
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageFoldersFolderTypeSheet(
    onDismiss: () -> Unit,
    onSelect: (com.eight87.whisperboy.data.library.FolderType) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.folder_type_dialog_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            com.eight87.whisperboy.data.library.FolderType.allOrdered.forEach { type ->
                TextButton(
                    onClick = { onSelect(type) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(folderTypeTitle(type)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(folderTypeSubtitle(type)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageFoldersSheet(
    roots: List<LibraryRoot>,
    onRemove: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.folder_section_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            roots.forEach { root ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = root.displayName, fontWeight = FontWeight.Medium)
                        Text(
                            text = stringResource(folderTypeTitle(root.folderType)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onRemove(root.treeUri) }) {
                        Text(stringResource(R.string.folder_remove_action))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    }
}

private fun folderTypeTitle(type: com.eight87.whisperboy.data.library.FolderType): Int = when (type) {
    com.eight87.whisperboy.data.library.FolderType.SingleFile -> R.string.folder_type_singlefile_title
    com.eight87.whisperboy.data.library.FolderType.SingleFolder -> R.string.folder_type_singlefolder_title
    com.eight87.whisperboy.data.library.FolderType.Root -> R.string.folder_type_root_title
    com.eight87.whisperboy.data.library.FolderType.Author -> R.string.folder_type_author_title
}

private fun folderTypeSubtitle(type: com.eight87.whisperboy.data.library.FolderType): Int = when (type) {
    com.eight87.whisperboy.data.library.FolderType.SingleFile -> R.string.folder_type_singlefile_subtitle
    com.eight87.whisperboy.data.library.FolderType.SingleFolder -> R.string.folder_type_singlefolder_subtitle
    com.eight87.whisperboy.data.library.FolderType.Root -> R.string.folder_type_root_subtitle
    com.eight87.whisperboy.data.library.FolderType.Author -> R.string.folder_type_author_subtitle
}

