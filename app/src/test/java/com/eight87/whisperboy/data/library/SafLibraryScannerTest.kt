package com.eight87.whisperboy.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for the pure ID-generation helpers — the bit of [SafLibraryScanner] that doesn't
 * need a SAF tree or `Context`. Walking-the-tree behaviour ships verification through the
 * D.6 smoke test (`scripts/library-smoke-test.sh`), where a real fixture folder is pushed to
 * `emulator-5554` and the scan output is asserted from logcat.
 */
class SafLibraryScannerTest {

    @Test
    fun `bookIdFor is stable across calls with the same input`() {
        val a = SafLibraryScanner.bookIdFor(TREE_URI, "Hound of the Baskervilles")
        val b = SafLibraryScanner.bookIdFor(TREE_URI, "Hound of the Baskervilles")
        assertEquals(a, b)
    }

    @Test
    fun `bookIdFor differs by treeUri`() {
        val a = SafLibraryScanner.bookIdFor(TREE_URI, "Same Path")
        val b = SafLibraryScanner.bookIdFor(OTHER_TREE_URI, "Same Path")
        assertNotEquals(a, b)
    }

    @Test
    fun `bookIdFor differs by relative path`() {
        val a = SafLibraryScanner.bookIdFor(TREE_URI, "Book One")
        val b = SafLibraryScanner.bookIdFor(TREE_URI, "Book Two")
        assertNotEquals(a, b)
    }

    @Test
    fun `bookIdFor produces a hex SHA-256 (64 chars)`() {
        val id = SafLibraryScanner.bookIdFor(TREE_URI, "Book")
        assertEquals(64, id.length)
        assertEquals(true, id.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `chapterIdFor is stable across calls`() {
        val bookId = SafLibraryScanner.bookIdFor(TREE_URI, "Book")
        assertEquals(
            SafLibraryScanner.chapterIdFor(bookId, 3),
            SafLibraryScanner.chapterIdFor(bookId, 3),
        )
    }

    @Test
    fun `chapterIdFor differs by chapter index`() {
        val bookId = SafLibraryScanner.bookIdFor(TREE_URI, "Book")
        assertNotEquals(
            SafLibraryScanner.chapterIdFor(bookId, 0),
            SafLibraryScanner.chapterIdFor(bookId, 1),
        )
    }

    private companion object {
        const val TREE_URI = "content://com.android.externalstorage.documents/tree/primary%3AAudiobooks"
        const val OTHER_TREE_URI = "content://com.android.externalstorage.documents/tree/primary%3ASD"
    }
}
