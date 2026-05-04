package com.eight87.whisperboy.data.library

import kotlinx.coroutines.flow.Flow

/**
 * Narrow data interface (Phase R.A pattern) for bookmark CRUD scoped to a single book.
 *
 * Phase H's [BookmarkScreen] and Phase G.5's auto-bookmark-on-sleep-timer-fire path both
 * depend on this contract. Auto-created bookmarks set `setBySleepTimer = true` so the
 * bookmark list can render them with a clock badge.
 */
interface BookmarkSource {

    fun observeBookmarksForBook(bookId: String): Flow<List<BookmarkEntity>>

    suspend fun addBookmark(
        bookId: String,
        chapterId: String?,
        title: String?,
        positionInBookMs: Long,
        setBySleepTimer: Boolean = false,
    )

    suspend fun renameBookmark(id: String, title: String?)

    suspend fun deleteBookmark(id: String)
}
