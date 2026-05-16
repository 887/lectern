package com.eight87.whisperboy

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute : NavKey

/**
 * Full-screen player surface (Phase F.1) for a specific book. The parent's integration step
 * (post-merge) wires `LibraryScreen`'s book-tap to `backStack.add(PlaybackRoute(bookId))`.
 */
@Serializable data class PlaybackRoute(val bookId: String) : NavKey
