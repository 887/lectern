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
}
