package com.eight87.whisperboy

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute : NavKey

/**
 * Full-screen player surface (Phase F.1) for a specific book. The parent's integration step
 * (post-merge) wires `LibraryScreen`'s book-tap to `backStack.add(PlaybackRoute(bookId))`.
 */
@Serializable data class PlaybackRoute(val bookId: String) : NavKey

/**
 * Per-book DuckDuckGo image-search surface for cover art (cover-art.md Phase B). Pushed
 * onto the back stack by the library long-press action sheet's "Search online" item; the
 * library-side integration ships in a coexisting inch.
 */
@Serializable data class CoverSearchRoute(val bookId: String) : NavKey
