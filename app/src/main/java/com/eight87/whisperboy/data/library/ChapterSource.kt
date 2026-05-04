package com.eight87.whisperboy.data.library

import kotlinx.coroutines.flow.Flow

/**
 * Narrow data interface (Phase R.A pattern) for read access to a book's chapters.
 *
 * Phase F's player chapter list and Phase D.2's scanner both depend on this contract; neither
 * touches [ChapterDao] directly.
 */
interface ChapterSource {

    fun observeChaptersForBook(bookId: String): Flow<List<ChapterEntity>>

    suspend fun chaptersFor(bookId: String): List<ChapterEntity>
}
