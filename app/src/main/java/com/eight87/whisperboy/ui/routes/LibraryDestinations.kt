package com.eight87.whisperboy.ui.routes

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eight87.whisperboy.AuthorDetailRoute
import com.eight87.whisperboy.LibraryFoldersRoute
import com.eight87.whisperboy.ui.library.AuthorDetailScreen
import com.eight87.whisperboy.ui.settings.LibrarySettingsScreen
import kotlinx.coroutines.launch

/**
 * Library destinations. After the catalog port, the only Library
 * sub-page that survives behind a route is the folder-management
 * screen ([LibrarySettingsScreen]); the per-knob default screens
 * (sort / grid mode / scan filters) collapsed into dialog pickers
 * on the Settings root.
 */
@Suppress("NOTHING_TO_INLINE") internal inline fun EntryProviderScope<NavKey>.registerLibraryEntries(scope: RouteScope) {
    val graph = scope.graph
    val backStack = scope.backStack
    val coroutineScope = scope.coroutineScope

    entry<AuthorDetailRoute> { route ->
        AuthorDetailScreen(
            authorName = route.authorName,
            bookSource = graph.bookSource,
            onBack = { backStack.removeLastOrNull() },
            onBookTap = { bookId ->
                coroutineScope.launch { graph.bookCommands.playBook(bookId) }
                scope.openSheet()
            },
            modifier = Modifier.safeDrawingPadding(),
        )
    }
    entry<LibraryFoldersRoute> {
        LibrarySettingsScreen(
            persistedUriPermissionStore = graph.persistedUriPermissionStore,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding(),
        )
    }
}
