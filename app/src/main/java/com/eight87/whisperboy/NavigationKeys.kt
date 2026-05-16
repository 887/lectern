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
