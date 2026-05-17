package com.eight87.whisperboy.data.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-backed Room round-trip tests for [BookDao].
 *
 * Uses an in-memory [LibraryDatabase] (no SQLite file on disk; cleared between tests).
 * Covers:
 *  - basic upsert + observe ordering
 *  - delete + reactive observe update
 *  - R.F.9 [BookDao.observeByAuthor] filter + NOCASE title ordering, including null/blank authors
 *  - reactive [BookDao.setLastPlayedAt] update propagates through the observe flow
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookDaoTest {

    private lateinit var db: LibraryDatabase
    private lateinit var dao: BookDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.bookDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun book(
        id: String,
        title: String,
        author: String? = null,
        active: Boolean = true,
        lastPlayedAt: Long? = null,
    ): BookEntity = BookEntity(
        bookId = id,
        treeUriString = "content://tree/$id",
        relativePath = "$id.m4b",
        title = title,
        author = author,
        durationMs = 60_000L,
        active = active,
        lastPlayedAt = lastPlayedAt,
    )

    @Test
    fun `observeActive emits inserted books in title NOCASE order`() = runTest {
        dao.upsertAll(
            listOf(
                book("c", "Charlie"),
                book("a", "alpha"),
                book("b", "Bravo"),
            )
        )

        val observed = dao.observeActive().first()
        assertEquals(3, observed.size)
        assertEquals(listOf("alpha", "Bravo", "Charlie"), observed.map { it.title })
    }

    @Test
    fun `delete removes row and observeActive reflects the new set`() = runTest {
        dao.upsertAll(listOf(book("a", "Alpha"), book("b", "Bravo")))
        assertEquals(2, dao.observeActive().first().size)

        dao.deleteById("a")

        val observed = dao.observeActive().first()
        assertEquals(1, observed.size)
        assertEquals("b", observed.single().bookId)
    }

    @Test
    fun `observeByAuthor filters to that author and excludes nulls and blanks`() = runTest {
        dao.upsertAll(
            listOf(
                book("1", "Echo", author = "Asimov"),
                book("2", "alpha", author = "Asimov"),
                book("3", "Beta", author = "Bradbury"),
                book("4", "Gamma", author = null),
                book("5", "Delta", author = ""),
                book("6", "Foxtrot", author = "  "),
            )
        )

        val asimov = dao.observeByAuthor("Asimov").first()
        assertEquals(listOf("alpha", "Echo"), asimov.map { it.title })

        val bradbury = dao.observeByAuthor("Bradbury").first()
        assertEquals(listOf("Beta"), bradbury.map { it.title })

        // Null / blank authors do not match a real-name filter.
        assertTrue(dao.observeByAuthor("").first().none { it.bookId in listOf("1", "2", "3") })
        assertTrue(dao.observeByAuthor("Asimov").first().none { it.author == null })
    }

    @Test
    fun `observeByAuthor is case-insensitive (NOCASE collation)`() = runTest {
        dao.upsertAll(
            listOf(
                book("1", "One", author = "Le Guin"),
                book("2", "Two", author = "LE GUIN"),
                book("3", "Three", author = "le guin"),
                book("4", "Four", author = "Other"),
            )
        )

        val matches = dao.observeByAuthor("le guin").first()
        assertEquals(3, matches.size)
        // Ordered by title COLLATE NOCASE.
        assertEquals(listOf("One", "Three", "Two"), matches.map { it.title })
    }

    @Test
    fun `observeByAuthor skips inactive (soft-deleted) rows`() = runTest {
        dao.upsertAll(
            listOf(
                book("1", "Live", author = "Author", active = true),
                book("2", "Dead", author = "Author", active = false),
            )
        )

        val active = dao.observeByAuthor("Author").first()
        assertEquals(1, active.size)
        assertEquals("1", active.single().bookId)
    }

    @Test
    fun `setLastPlayedAt updates the row and observe emits the new value`() = runTest {
        dao.upsert(book("a", "Alpha", lastPlayedAt = null))
        assertNull(dao.observeById("a").first()?.lastPlayedAt)

        dao.setLastPlayedAt("a", 1_234L)

        val row = dao.observeById("a").first()
        assertNotNull(row)
        assertEquals(1_234L, row!!.lastPlayedAt)
    }
}
