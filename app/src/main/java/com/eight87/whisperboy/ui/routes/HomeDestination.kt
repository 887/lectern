package com.eight87.whisperboy.ui.routes

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eight87.whisperboy.HomeRoute
import com.eight87.whisperboy.LibraryFoldersRoute
import com.eight87.whisperboy.SettingsRoute
import com.eight87.whisperboy.ui.home.HomeScreen
import kotlinx.coroutines.launch

/**
 * Home (library cover grid). Tapping a book fires `bookCommands.playBook` and
 * animates the now-playing sheet open via [RouteScope.openSheet]. The sheet
 * itself is an overlay in `WhisperboyApp`, not a route.
 */
@Suppress("NOTHING_TO_INLINE") internal inline fun EntryProviderScope<NavKey>.registerHomeEntries(scope: RouteScope) {
    val graph = scope.graph
    val backStack = scope.backStack
    val coroutineScope = scope.coroutineScope

    entry<HomeRoute> {
        HomeScreen(
            persistedUriPermissionStore = graph.persistedUriPermissionStore,
            bookSource = graph.bookSource,
            libraryUiSettings = graph.libraryUiSettings,
            libraryRescanCoordinator = graph.libraryRescanCoordinator,
            onBookTap = { bookId ->
                coroutineScope.launch { graph.bookCommands.playBook(bookId) }
                scope.openSheet()
            },
            onSettingsClick = { backStack.add(SettingsRoute) },
            onLibraryFoldersClick = { backStack.add(LibraryFoldersRoute) },
            modifier = Modifier.safeDrawingPadding(),
        )
    }
}
