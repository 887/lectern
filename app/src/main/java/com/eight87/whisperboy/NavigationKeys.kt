package com.eight87.whisperboy

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute : NavKey

/**
 * Full-screen player surface (Phase F.1) for a specific book.
 *
 * Retired from the user-driven nav path: the book-tap in [com.eight87.whisperboy.ui.library.LibraryScreen]
 * now fires `bookCommands.playBook(bookId)` and animates the in-place
 * [com.eight87.whisperboy.ui.playback.NowPlayingSheet] from peek (mini-player) to fully
 * expanded, NOT a back-stack push. Retained for future deeplink entry — e.g.
 * notification tap, Android Auto launch — which will hand off to the sheet's
 * `animateTo(1f)` rather than register a nav entry. See tonearmboy's
 * `pendingDeeplink` pattern in `TonearmboyApp.kt` for the shape that lands when
 * deeplinks ship.
 */
@Serializable data class PlaybackRoute(val bookId: String) : NavKey

/**
 * Per-book DuckDuckGo image-search surface for cover art (cover-art.md Phase B). Pushed
 * onto the back stack by the library long-press action sheet's "Search online" item; the
 * library-side integration ships in a coexisting inch.
 */
@Serializable data class CoverSearchRoute(val bookId: String) : NavKey

/**
 * Phase K.1 — settings root surface. Pushed from the library top-app-bar
 * overflow menu's "Settings" item. Hosts the category list that branches
 * into Playback (K.2) / Sleep timer (K.3) / Library (K.4) / Theme (K.5) /
 * About (K.6) sub-pages.
 */
@Serializable data object SettingsRoute : NavKey

/**
 * Phase K.6 — About sub-page. Pushed from `SettingsRoute`. Shows app
 * icon, version, license, clean-room "spiritual sibling" credit for
 * Voice, GitHub link, and (eventually) the open-source-licenses
 * sub-page tracked in `docs/plans/oss-licenses.md`.
 */
@Serializable data object AboutRoute : NavKey

/**
 * Phase K.4 (partial) — Library folders sub-page. Pushed from Settings.
 * Lists configured roots with their `FolderType` + Remove action; a FAB
 * adds a new root via the SAF tree picker.
 */
@Serializable data object LibraryFoldersRoute : NavKey

/**
 * Phase K.5 — Theme sub-page. Pushed from Settings. Hosts the theme-mode
 * radio group (Light / Dark / Follow system) plus the dynamic-color
 * (Material You) toggle. Selection persists via [com.eight87.whisperboy.data.theme.ThemeSettings].
 */
@Serializable data object ThemeSettingsRoute : NavKey
