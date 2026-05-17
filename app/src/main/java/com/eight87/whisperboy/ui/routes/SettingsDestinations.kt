package com.eight87.whisperboy.ui.routes

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eight87.whisperboy.AboutRoute
import com.eight87.whisperboy.LibraryFoldersRoute
import com.eight87.whisperboy.LicensesRoute
import com.eight87.whisperboy.PlaybackSettingsRoute
import com.eight87.whisperboy.SettingsRoute
import com.eight87.whisperboy.SleepTimerSettingsRoute
import com.eight87.whisperboy.ThemeSettingsRoute
import com.eight87.whisperboy.ui.settings.AboutScreen
import com.eight87.whisperboy.ui.settings.LicensesScreen
import com.eight87.whisperboy.ui.settings.PlaybackSettingsScreen
import com.eight87.whisperboy.ui.settings.SettingsScreen
import com.eight87.whisperboy.ui.settings.SleepTimerSettingsScreen
import com.eight87.whisperboy.ui.settings.ThemeSettingsScreen

/**
 * Phase K — Settings hub + leaf sub-pages (Playback, Sleep timer, Theme,
 * About, Licenses). The Library sub-tree lives in `LibraryDestinations` to
 * keep this file scoped to non-library settings.
 */
@Suppress("NOTHING_TO_INLINE") internal inline fun EntryProviderScope<NavKey>.registerSettingsEntries(scope: RouteScope) {
    val graph = scope.graph
    val backStack = scope.backStack

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
            onPlaybackClick = { backStack.add(PlaybackSettingsRoute) },
            onSleepTimerClick = { backStack.add(SleepTimerSettingsRoute) },
            onThemeClick = { backStack.add(ThemeSettingsRoute) },
            modifier = Modifier.safeDrawingPadding(),
        )
    }
    entry<PlaybackSettingsRoute> {
        // Phase K.2 — playback defaults + seek seconds + equalizer launcher.
        PlaybackSettingsScreen(
            playbackSettings = graph.playbackSettings,
            onBack = { backStack.removeLastOrNull() },
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
    entry<AboutRoute> {
        // Phase K.6 — About sub-page.
        AboutScreen(
            onBack = { backStack.removeLastOrNull() },
            onLicensesClick = { backStack.add(LicensesRoute) },
            modifier = Modifier.safeDrawingPadding(),
        )
    }
    entry<LicensesRoute> {
        // oss-licenses Phase B — Open-source licenses sub-page.
        LicensesScreen(
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding(),
        )
    }
}
