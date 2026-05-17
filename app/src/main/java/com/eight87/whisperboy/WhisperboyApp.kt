package com.eight87.whisperboy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.eight87.whisperboy.playback.PlaybackUiState
import com.eight87.whisperboy.ui.coverart.SelectCoverFromInternet
import com.eight87.whisperboy.ui.home.HomeScreen
import com.eight87.whisperboy.ui.onboarding.OnboardingFirstScanScreen
import com.eight87.whisperboy.ui.onboarding.OnboardingFolderPickerScreen
import com.eight87.whisperboy.ui.onboarding.OnboardingPermissionsScreen
import com.eight87.whisperboy.ui.onboarding.OnboardingWelcomeScreen
import com.eight87.whisperboy.ui.playback.NowPlayingSheet
import com.eight87.whisperboy.ui.settings.AboutScreen
import com.eight87.whisperboy.ui.settings.LibraryFoldersScreen
import com.eight87.whisperboy.ui.settings.SettingsScreen
import com.eight87.whisperboy.ui.settings.SleepTimerSettingsScreen
import com.eight87.whisperboy.ui.settings.ThemeSettingsScreen
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
 *
 * **Phase L:** initial back-stack key is chosen from [OnboardingSettings.completed]:
 *
 * - `null` (DataStore hasn't emitted yet on this cold start): render nothing —
 *   the Android 12+ system splash stays up until the first emission lands.
 *   Avoids a flash of either Home or Welcome on the wrong path. The DataStore
 *   emits its first value within a frame or two of subscription on every device
 *   we test on, so this is invisible in practice.
 * - `false`: start at `OnboardingWelcomeRoute`. Completion replaces the stack with `HomeRoute`.
 * - `true`: start directly at `HomeRoute`. Existing users never see onboarding.
 */
@Composable
fun WhisperboyApp() {
    val graph = (LocalContext.current.applicationContext as WhisperboyApplication).graph

    // Wait for the onboarding flag to emit at least once before constructing the
    // NavDisplay. `initialValue = null` distinguishes "still waiting" from the
    // two real states (false / true). No splash composable of our own — the
    // Android 12+ system splash is already on screen for the first frame; we
    // simply don't replace it until we know where to send the user.
    val onboardingCompleted by graph.onboardingSettings.completed.collectAsStateWithLifecycle(initialValue = null)
    val initialKey: NavKey? = when (onboardingCompleted) {
        null -> null
        true -> HomeRoute
        false -> OnboardingWelcomeRoute
    }
    if (initialKey == null) return

    val backStack = rememberNavBackStack(initialKey)
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

    // When the mini-player peek is visible, pad the NavDisplay's bottom by the peek
    // height so library chrome (FAB, lists) doesn't sit underneath the peek bar.
    // Matches tonearmboy's `libraryBottomPad` pattern. Keep in sync with
    // `NowPlayingSheet.DEFAULT_PEEK_DP`.
    val playbackState by graph.nowPlayingState.state.collectAsStateWithLifecycle()
    val showMiniPlayer = playbackState is PlaybackUiState.Loaded
    val navDisplayBottomPad = if (showMiniPlayer) 80.dp else 0.dp

    // Replace the entire back stack with HomeRoute. Used by the onboarding
    // first-scan "Continue" button so back from Home doesn't re-enter the
    // onboarding flow.
    val finishOnboarding: () -> Unit = {
        backStack.clear()
        backStack.add(HomeRoute)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = navDisplayBottomPad)) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<OnboardingWelcomeRoute> {
                    OnboardingWelcomeScreen(
                        onGetStarted = { backStack.add(OnboardingPermissionsRoute) },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
                entry<OnboardingPermissionsRoute> {
                    OnboardingPermissionsScreen(
                        onNext = { backStack.add(OnboardingFolderPickerRoute) },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
                entry<OnboardingFolderPickerRoute> {
                    OnboardingFolderPickerScreen(
                        persistedUriPermissionStore = graph.persistedUriPermissionStore,
                        onNext = { backStack.add(OnboardingFirstScanRoute) },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
                entry<OnboardingFirstScanRoute> {
                    OnboardingFirstScanScreen(
                        libraryRescanCoordinator = graph.libraryRescanCoordinator,
                        bookSource = graph.bookSource,
                        chapterSource = graph.chapterSource,
                        onboardingSettings = graph.onboardingSettings,
                        onFinish = finishOnboarding,
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
                entry<HomeRoute> {
                    HomeScreen(
                        persistedUriPermissionStore = graph.persistedUriPermissionStore,
                        bookSource = graph.bookSource,
                        libraryUiSettings = graph.libraryUiSettings,
                        onBookTap = { bookId ->
                            scope.launch { graph.bookCommands.playBook(bookId) }
                            openSheet()
                        },
                        onSettingsClick = { backStack.add(SettingsRoute) },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
                entry<SettingsRoute> {
                    // Phase K.1 — settings root. Subcategory navigation
                    // (Playback / Sleep timer / Theme) lands when K.2 /
                    // K.3 / K.5 ship; the Library row navigates to the
                    // Phase K.4 partial `LibraryFoldersRoute`, and a
                    // "Rescan now" button sits inside the General card.
                    SettingsScreen(
                        libraryRescanCoordinator = graph.libraryRescanCoordinator,
                        onBack = { backStack.removeLastOrNull() },
                        onAboutClick = { backStack.add(AboutRoute) },
                        onLibraryFoldersClick = { backStack.add(LibraryFoldersRoute) },
                        onSleepTimerClick = { backStack.add(SleepTimerSettingsRoute) },
                        onThemeClick = { backStack.add(ThemeSettingsRoute) },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
                entry<SleepTimerSettingsRoute> {
                    // Phase K.3 — sleep-timer defaults + auto-arm window.
                    SleepTimerSettingsScreen(
                        sleepTimerSettings = graph.sleepTimerSettings,
                        onBack = { backStack.removeLastOrNull() },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
                entry<ThemeSettingsRoute> {
                    // Phase K.5 — theme mode + dynamic-color toggle.
                    ThemeSettingsScreen(
                        themeSettings = graph.themeSettings,
                        onBack = { backStack.removeLastOrNull() },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
                entry<LibraryFoldersRoute> {
                    // Phase K.4 (partial) — folder management lives in Settings now.
                    LibraryFoldersScreen(
                        persistedUriPermissionStore = graph.persistedUriPermissionStore,
                        onBack = { backStack.removeLastOrNull() },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
                entry<AboutRoute> {
                    // Phase K.6 — About sub-page.
                    AboutScreen(
                        onBack = { backStack.removeLastOrNull() },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
                entry<CoverSearchRoute> { route ->
                    // cover-art Phase B.7 — the per-book DuckDuckGo image-search surface.
                    SelectCoverFromInternet(
                        bookId = route.bookId,
                        coverApi = graph.coverApi,
                        bookSource = graph.bookSource,
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
        }

        // Overlay sheet — peek bar (mini-player) along the bottom, expands to
        // the full PlaybackScreen as `sheetProgress` runs 0 → 1. Self-hides
        // when no Loaded playback state; no slot reserved when nothing's
        // playing.
        NowPlayingSheet(
            nowPlayingState = graph.nowPlayingState,
            transportCommands = graph.transportCommands,
            chapterSource = graph.chapterSource,
            playbackSettings = graph.playbackSettings,
            sleepTimerCommands = graph.sleepTimerCommands,
            sheetProgress = sheetProgress,
            onCollapse = closeSheet,
        )
    }
}
