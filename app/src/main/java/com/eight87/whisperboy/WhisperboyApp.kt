package com.eight87.whisperboy

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
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
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        },
    )
}
