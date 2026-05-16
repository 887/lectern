package com.eight87.whisperboy.data.library

import kotlinx.coroutines.flow.Flow

/**
 * Narrow data interface (Phase R.A pattern) for read access to the book catalog.
 *
 * Composables and ViewModels depend on this contract, never on the concrete repository
 * (which lands in Phase D.4) or on [BookDao] directly. The two-method footprint is the
 * minimum the library / player / detail screens need.
 *
 * Returns [BookEntity] directly for now; if Phase D.2's scanner produces fields the cache
 * doesn't keep, R.F.4 (Book vs ScannedBook split) will introduce a domain wrapper here.
 */
interface BookSource {

    fun observeBooks(): Flow<List<BookEntity>>

    fun observeBook(id: String): Flow<BookEntity?>

    suspend fun search(query: String): List<BookEntity>

    /**
     * Phase E.5 — book actions. Mark / unmark completion (sets / clears `completedAt`),
     * forget a book entirely (deletes the row + cascading bookmarks/chapters via FK).
     *
     * Kept on the same interface for now — splitting into a separate `BookCommandSource`
     * facet earns its keep when a second consumer (e.g. multi-select bar) appears that
     * wants write access without read access. Currently both consumers also read books,
     * so the single interface is fine.
     */
    suspend fun markCompleted(bookId: String)

    suspend fun markNotStarted(bookId: String)

    suspend fun forgetBook(bookId: String)
}
