package com.eight87.whisperboy.ui.library

import com.eight87.whisperboy.data.library.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortingTest {

    private fun book(
        bookId: String,
        title: String,
        author: String? = null,
        lastPlayedAt: Long? = null,
    ): BookEntity = BookEntity(
        bookId = bookId,
        treeUriString = "content://x",
        relativePath = bookId,
        title = title,
        author = author,
        durationMs = 1000L,
        lastPlayedAt = lastPlayedAt,
    )

    @Test
    fun `sortedBooks Title sorts alphabetically by title`() {
        val out = sortedBooks(
            listOf(
                book("c", "Charlie"),
                book("a", "Alpha"),
                book("b", "Bravo"),
            ),
            BookSortKey.Title,
        )
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), out.map { it.title })
    }

    @Test
    fun `sortedBooks Author groups unknown-author last`() {
        val out = sortedBooks(
            listOf(
                book("c", "C", author = null),
                book("a", "A", author = "Asimov"),
                book("b", "B", author = "Bradbury"),
                book("d", "D", author = ""),
            ),
            BookSortKey.Author,
        )
        assertEquals(listOf("Asimov", "Bradbury", null, ""), out.map { it.author })
    }

    @Test
    fun `sortedBooks Recent puts most-recently-played first, never-played last`() {
        val out = sortedBooks(
            listOf(
                book("never", "Never", lastPlayedAt = null),
                book("old", "Old", lastPlayedAt = 1000L),
                book("new", "New", lastPlayedAt = 5000L),
            ),
            BookSortKey.Recent,
        )
        assertEquals(listOf("New", "Old", "Never"), out.map { it.title })
    }

    @Test
    fun `sectionStartsFor Title returns first-letter starts`() {
        val out = sectionStartsFor(
            listOf(
                book("a", "Alpha"),
                book("a2", "Apple"),
                book("b", "Bravo"),
                book("4", "4chan"),
            ),
            BookSortKey.Title,
        )
        assertEquals(listOf(0 to "A", 2 to "B", 3 to "#"), out)
    }

    @Test
    fun `sectionStartsFor Author labels unknown-author as question mark`() {
        val out = sectionStartsFor(
            listOf(
                book("a", "A", author = "Asimov"),
                book("b", "B", author = null),
                book("c", "C", author = null),
            ),
            BookSortKey.Author,
        )
        assertEquals(listOf(0 to "A", 1 to "?"), out)
    }

    @Test
    fun `sectionStartsFor Recent buckets by age relative to nowMs`() {
        val now = 100L * 24 * 60 * 60 * 1000 // arbitrary "now"
        val oneDay = 24L * 60 * 60 * 1000
        val out = sectionStartsFor(
            listOf(
                book("today", "T", lastPlayedAt = now - oneDay / 2),
                book("week", "W", lastPlayedAt = now - 3 * oneDay),
                book("month", "M", lastPlayedAt = now - 14 * oneDay),
                book("older", "O", lastPlayedAt = now - 60 * oneDay),
                book("never", "N", lastPlayedAt = null),
            ),
            BookSortKey.Recent,
            nowMs = now,
        )
        assertEquals(
            listOf(0 to "Today", 1 to "Week", 2 to "Month", 3 to "Older", 4 to "Never"),
            out,
        )
    }

    @Test
    fun `sectionStartsFor empty list returns empty`() {
        assertEquals(emptyList<Pair<Int, String>>(), sectionStartsFor(emptyList(), BookSortKey.Title))
    }
}
