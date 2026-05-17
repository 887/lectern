package com.eight87.whisperboy.ui.routes

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eight87.whisperboy.LibraryFoldersRoute
import com.eight87.whisperboy.LibraryGridModeDefaultRoute
import com.eight87.whisperboy.LibraryScanFiltersRoute
import com.eight87.whisperboy.LibrarySortDefaultRoute
import com.eight87.whisperboy.ui.settings.LibraryGridModeDefaultScreen
import com.eight87.whisperboy.ui.settings.LibraryScanFiltersScreen
import com.eight87.whisperboy.ui.settings.LibrarySettingsScreen
import com.eight87.whisperboy.ui.settings.LibrarySortDefaultScreen

/**
 * Phase K.4 — Library settings hub + its three sub-screens
 * (sort default, grid-mode default, scan filters). Reached from the Settings
 * root or directly from the library top-bar overflow.
 */
@Suppress("NOTHING_TO_INLINE") internal inline fun EntryProviderScope<NavKey>.registerLibraryEntries(scope: RouteScope) {
    val graph = scope.graph
    val backStack = scope.backStack

    entry<LibraryFoldersRoute> {
        // Phase K.4 — Library settings hub (folder list + add FAB +
        // three sub-screen rows). Route key kept as LibraryFoldersRoute
        // so persisted nav stacks survive the K.4 partial → full rename.
        LibrarySettingsScreen(
            persistedUriPermissionStore = graph.persistedUriPermissionStore,
            onBack = { backStack.removeLastOrNull() },
            onSortDefaultClick = { backStack.add(LibrarySortDefaultRoute) },
            onGridModeDefaultClick = { backStack.add(LibraryGridModeDefaultRoute) },
            onScanFiltersClick = { backStack.add(LibraryScanFiltersRoute) },
            modifier = Modifier.safeDrawingPadding(),
        )
    }
    entry<LibrarySortDefaultRoute> {
        // Phase K.4 sub-screen — default sort radio group.
        LibrarySortDefaultScreen(
            libraryUiSettings = graph.libraryUiSettings,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding(),
        )
    }
    entry<LibraryGridModeDefaultRoute> {
        // Phase K.4 sub-screen — default grid mode radio group.
        LibraryGridModeDefaultScreen(
            libraryUiSettings = graph.libraryUiSettings,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding(),
        )
    }
    entry<LibraryScanFiltersRoute> {
        // Phase K.4 sub-screen — scan-filter checkboxes; toggling triggers
        // a forced rescan + snackbar.
        LibraryScanFiltersScreen(
            libraryScanFilterSettings = graph.libraryScanFilterSettings,
            libraryRescanCoordinator = graph.libraryRescanCoordinator,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding(),
        )
    }
}
