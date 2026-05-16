package com.eight87.whisperboy.playback

import com.eight87.whisperboy.data.library.BookEntity
import com.eight87.whisperboy.data.library.ChapterEntity

/**
 * UI projection of the playback session.
 *
 * Phase F.2 ships three states: [Idle] (no book targeted, no [androidx.media3.session.MediaController]
 * connection yet), [Loading] (controller connect in flight or book row not yet fetched), and
 * [Loaded] (we know which book + chapter is current, and have a live position).
 *
 * The split keeps composables free of nullable book/chapter fields in the happy path — the player
 * surface renders only against [Loaded], the route entry shows a spinner against [Loading], and
 * an error/empty banner against [Idle] when the book can't be found.
 */
sealed interface PlaybackUiState {

    /** No active session; nothing to show. */
    data object Idle : PlaybackUiState

    /** Controller connection in flight, or book row hasn't arrived yet. */
    data object Loading : PlaybackUiState

    /** Book not found in [com.eight87.whisperboy.data.library.BookSource]. Renders error UI. */
    data object BookNotFound : PlaybackUiState

    /**
     * Active session against a known book.
     *
     * @param positionInBookMs Cumulative position across all chapters in the book.
     *   Ticker (R.C.5) writes this field at ~250ms while [isPlaying]; otherwise event-driven.
     * @param currentChapter Current chapter, or `null` when the book has no chapters (degenerate
     *   case — the scrubber falls back to [BookEntity.durationMs]).
     */
    data class Loaded(
        val book: BookEntity,
        val currentChapter: ChapterEntity?,
        val positionInBookMs: Long,
        val isPlaying: Boolean,
        val speed: Float,
        val skipSilenceEnabled: Boolean,
        val gainDb: Float,
    ) : PlaybackUiState
}
