package com.eight87.whisperboy

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.eight87.whisperboy.ui.coverart.SelectCoverFromInternet
import com.eight87.whisperboy.ui.home.HomeScreen
import com.eight87.whisperboy.ui.playback.PlaybackScreen

@Composable
fun WhisperboyApp() {
    val backStack = rememberNavBackStack(HomeRoute)
    val graph = (LocalContext.current.applicationContext as WhisperboyApplication).graph

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeScreen(
                    persistedUriPermissionStore = graph.persistedUriPermissionStore,
                    libraryRescanCoordinator = graph.libraryRescanCoordinator,
                    bookSource = graph.bookSource,
                    libraryUiSettings = graph.libraryUiSettings,
                    nowPlayingState = graph.nowPlayingState,
                    transportCommands = graph.transportCommands,
                    onBookTap = { bookId -> backStack.add(PlaybackRoute(bookId)) },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
            entry<CoverSearchRoute> { route ->
                // cover-art Phase B.7 — the per-book DuckDuckGo image-search surface. The
                // library-side action-sheet entry point that pushes this route is owned by
                // a coexisting inch; this entry just renders the destination.
                SelectCoverFromInternet(
                    bookId = route.bookId,
                    coverApi = graph.coverApi,
                    bookSource = graph.bookSource,
                    coverStore = graph.coverStore,
                    okHttpClient = graph.okHttpClient,
                    onClose = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
            entry<PlaybackRoute> { route ->
                // When the route lands, kick off playback for the target bookId. The controller's
                // `playBook` is idempotent against re-entry on the same bookId because the underlying
                // MediaController only restarts when the media set actually changes.
                LaunchedEffect(route.bookId) {
                    graph.bookCommands.playBook(route.bookId)
                }
                PlaybackScreen(
                    state = graph.nowPlayingState,
                    transport = graph.transportCommands,
                    chapterSource = graph.chapterSource,
                    playbackSettings = graph.playbackSettings,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        },
    )
}
