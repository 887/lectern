package com.eight87.whisperboy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.eight87.whisperboy.ui.coverart.SelectCoverFromInternet
import com.eight87.whisperboy.ui.home.HomeScreen
import com.eight87.whisperboy.ui.playback.NowPlayingSheet
import kotlinx.coroutines.launch

/**
 * Composition root.
 *
 * The now-playing surface (mini-player + full [com.eight87.whisperboy.ui.playback.PlaybackScreen])
 * is hosted as a single overlay **sheet** at this root via [NowPlayingSheet], NOT
 * pushed onto the back stack as a route. Tapping a book in the library fires
 * `playBook` and animates the sheet open 0 → 1; the mini-player is always
 * visible as a peek along the bottom whenever there's a Loaded playback state.
 * Modeled on tonearmboy's `TonearmboyApp` Auxio-style overlay.
 *
 * Sheet progress is owned here so the fast-ticking drag updates do not
 * propagate into the library's recomposition path (cold-start-perf C.1).
 */
@Composable
fun WhisperboyApp() {
    val backStack = rememberNavBackStack(HomeRoute)
    val graph = (LocalContext.current.applicationContext as WhisperboyApplication).graph
    val scope = rememberCoroutineScope()

    // Sheet progress 0 (collapsed, mini-player only) → 1 (full player).
    // Not `rememberSaveable` — tonearmboy doesn't bother either; on process
    // restart the sheet starts collapsed and the controller's persisted
    // state re-emerges into the peek bar naturally.
    val sheetProgress = remember { Animatable(0f) }

    val openSheet: () -> Unit = { scope.launch { sheetProgress.animateTo(1f) } }
    val closeSheet: () -> Unit = { scope.launch { sheetProgress.animateTo(0f) } }

    // Two-stage back: collapse the sheet first if it's open, otherwise the
    // NavDisplay's own onBack pops the back stack (and exits on HomeRoute).
    BackHandler(enabled = sheetProgress.value > 0f) { closeSheet() }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        onBookTap = { bookId ->
                            scope.launch { graph.bookCommands.playBook(bookId) }
                            openSheet()
                        },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
                entry<CoverSearchRoute> { route ->
                    // cover-art Phase B.7 — the per-book DuckDuckGo image-search surface.
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
                // PlaybackRoute is intentionally NOT registered: the user-driven
                // path through to the player goes via NowPlayingSheet (peek →
                // expand), not via a nav-stack push. PlaybackRoute is retained
                // in NavigationKeys.kt for future deeplinks (notification tap,
                // Auto launch) which will hand off to the sheet animate-to-1f.
            },
        )

        // Overlay sheet — peek bar (mini-player) along the bottom, expands to
        // the full PlaybackScreen as `sheetProgress` runs 0 → 1. Self-hides
        // when no Loaded playback state; no slot reserved when nothing's
        // playing.
        NowPlayingSheet(
            nowPlayingState = graph.nowPlayingState,
            transportCommands = graph.transportCommands,
            chapterSource = graph.chapterSource,
            playbackSettings = graph.playbackSettings,
            sheetProgress = sheetProgress,
            onCollapse = closeSheet,
        )
    }
}
