package com.eight87.whisperboy.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eight87.whisperboy.data.library.BookSource
import com.eight87.whisperboy.data.library.LibraryUiSettings
import com.eight87.whisperboy.data.library.PersistedUriPermissionStore
import com.eight87.whisperboy.ui.library.LibraryScreen

/**
 * Entry-route hand-off to the library grid.
 *
 * First-run is owned by the Phase L onboarding flow under `ui/onboarding/`,
 * which the root [com.eight87.whisperboy.WhisperboyApp] routes to before
 * `HomeRoute` when `OnboardingSettings.completed == false`. By the time
 * `HomeRoute` is reachable the user has at least one persisted SAF root and
 * a first-scan attempt has landed, so this composable is just a narrow
 * forward to [LibraryScreen].
 *
 * Kept as a thin shim (rather than wiring `LibraryScreen` directly into the
 * `HomeRoute` entry block in `WhisperboyApp`) so post-Phase-L additions —
 * "Welcome back" banner, recently-played carousel, a future "Continue
 * listening" row — have a single composable to grow into without bloating
 * the nav surface.
 */
@Composable
fun HomeScreen(
    persistedUriPermissionStore: PersistedUriPermissionStore,
    bookSource: BookSource,
    libraryUiSettings: LibraryUiSettings,
    onBookTap: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LibraryScreen(
        bookSource = bookSource,
        persistedUriPermissionStore = persistedUriPermissionStore,
        libraryUiSettings = libraryUiSettings,
        onBookTap = onBookTap,
        onSettingsClick = onSettingsClick,
        modifier = modifier,
    )
}
