package com.eight87.whisperboy.data.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eight87.whisperboy.data.playback.PlaybackSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
 * Integration test for [LibraryRepository] — exercises every narrow source
 * ([BookSource], [ChapterSource], [BookmarkSource], [ScanWriter]) against an
 * in-memory [LibraryDatabase] + a real [CoverStore] + a stub [PlaybackSettings].
 *
 * The point of this test is the wiring across the four facets in the same impl:
 *  - `ScanWriter.applyScan` inserts a book + chapters atomically.
 *  - `BookSource.observeBooks` reflects the new book.
 *  - `ChapterSource.observeChaptersForBook` returns chapters in order.
 *  - `BookmarkSource.addBookmark` + `observeBookmarksForBook` round-trip a bookmark.
 *  - `BookmarkSource.deleteBookmark` removes it.
 *
 * Per-component tests already pin DAO behaviour ([BookDaoTest]); this test pins
 * the *composition* — the place where wires get crossed in practice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryRepositoryTest {

    private lateinit var db: LibraryDatabase
    private lateinit var repo: LibraryRepository
    private lateinit var coverStore: CoverStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        coverStore = CoverStore(context)
        repo = LibraryRepository(db, coverStore, StubPlaybackSettings())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `applyScan then observeBooks emits the new book`() = runTest {
        repo.applyScan(snapshotWithOneBook(bookId = "b1", title = "The Test"))

        val books = repo.observeBooks().first()
        assertEquals(1, books.size)
        val book = books.single()
        assertEquals("b1", book.bookId)
        assertEquals("The Test", book.title)
        assertTrue("expected book.active = true after applyScan", book.active)
    }

    @Test
    fun `applyScan seeds chapters and observeChaptersForBook returns them in order`() = runTest {
        repo.applyScan(
            ScanSnapshot(
                books = listOf(
                    ScannedBook(
                        bookId = "b1",
                        treeUriString = "content://tree/r1",
                        relativePath = "the-test/",
                        title = "The Test",
                        author = "Tester",
                        durationMs = 30_000L,
                        chapters = listOf(
                            ScannedChapter(
                                chapterId = "c1",
                                chapterIndex = 0,
                                title = "Chapter One",
                                durationMs = 10_000L,
                                fileUri = "content://tree/r1/the-test/c1.m4b",
                                positionInBookMs = 0L,
                            ),
                            ScannedChapter(
                                chapterId = "c2",
                                chapterIndex = 1,
                                title = "Chapter Two",
                                durationMs = 10_000L,
                                fileUri = "content://tree/r1/the-test/c2.m4b",
                                positionInBookMs = 10_000L,
                            ),
                            ScannedChapter(
                                chapterId = "c3",
                                chapterIndex = 2,
                                title = "Chapter Three",
                                durationMs = 10_000L,
                                fileUri = "content://tree/r1/the-test/c3.m4b",
                                positionInBookMs = 20_000L,
                            ),
                        ),
                    ),
                ),
            )
        )

        val chapters = repo.observeChaptersForBook("b1").first()
        assertEquals(3, chapters.size)
        assertEquals(listOf(0, 1, 2), chapters.map { it.chapterIndex })
        assertEquals(
            listOf("Chapter One", "Chapter Two", "Chapter Three"),
            chapters.map { it.title },
        )
        // chaptersFor is the suspend variant — same result.
        val chaptersSnapshot = repo.chaptersFor("b1")
        assertEquals(chapters.map { it.chapterId }, chaptersSnapshot.map { it.chapterId })
    }

    @Test
    fun `addBookmark roundtrips through observeBookmarksForBook`() = runTest {
        repo.applyScan(snapshotWithOneBook("b1", "The Test"))

        // Initially no bookmarks.
        assertEquals(0, repo.observeBookmarksForBook("b1").first().size)

        repo.addBookmark(
            bookId = "b1",
            chapterId = null,
            title = "the good bit",
            positionInBookMs = 12_345L,
            setBySleepTimer = false,
        )

        val bookmarks = repo.observeBookmarksForBook("b1").first()
        assertEquals(1, bookmarks.size)
        val bm = bookmarks.single()
        assertEquals("b1", bm.bookId)
        assertEquals("the good bit", bm.title)
        assertEquals(12_345L, bm.positionInBookMs)
        assertEquals(false, bm.setBySleepTimer)
        assertNotNull("addedAt must be populated", bm.addedAt)
    }

    @Test
    fun `deleteBookmark removes the row and flow emits empty`() = runTest {
        repo.applyScan(snapshotWithOneBook("b1", "The Test"))
        repo.addBookmark("b1", chapterId = null, title = "x", positionInBookMs = 1L, setBySleepTimer = false)
        repo.addBookmark("b1", chapterId = null, title = "y", positionInBookMs = 2L, setBySleepTimer = false)

        val before = repo.observeBookmarksForBook("b1").first()
        assertEquals(2, before.size)

        // Delete the first one.
        repo.deleteBookmark(before.first().bookmarkId)
        val mid = repo.observeBookmarksForBook("b1").first()
        assertEquals(1, mid.size)

        // Delete the rest.
        repo.deleteBookmark(mid.single().bookmarkId)
        val after = repo.observeBookmarksForBook("b1").first()
        assertEquals(0, after.size)
    }

    @Test
    fun `renameBookmark updates the title in place`() = runTest {
        repo.applyScan(snapshotWithOneBook("b1", "The Test"))
        repo.addBookmark("b1", chapterId = null, title = "old", positionInBookMs = 1L, setBySleepTimer = false)

        val id = repo.observeBookmarksForBook("b1").first().single().bookmarkId
        repo.renameBookmark(id, title = "new")

        val bm = repo.observeBookmarksForBook("b1").first().single()
        assertEquals("new", bm.title)
    }

    @Test
    fun `observeBook emits the inserted book and null for unknown id`() = runTest {
        repo.applyScan(snapshotWithOneBook("b1", "The Test"))

        val present = repo.observeBook("b1").first()
        assertNotNull(present)
        assertEquals("The Test", present!!.title)

        val absent = repo.observeBook("does-not-exist").first()
        assertNull(absent)
    }

    // --- helpers ---

    private fun snapshotWithOneBook(bookId: String, title: String): ScanSnapshot = ScanSnapshot(
        books = listOf(
            ScannedBook(
                bookId = bookId,
                treeUriString = "content://tree/r1",
                relativePath = "$bookId.m4b",
                title = title,
                author = "A",
                durationMs = 10_000L,
                chapters = listOf(
                    ScannedChapter(
                        chapterId = "$bookId-c0",
                        chapterIndex = 0,
                        title = "Only",
                        durationMs = 10_000L,
                        fileUri = "content://tree/r1/$bookId.m4b",
                        positionInBookMs = 0L,
                    ),
                ),
            ),
        ),
    )

    /**
     * Minimal [PlaybackSettings] stub — `applyScan` only reads `defaultSpeed` /
     * `defaultSkipSilence` / `defaultGainDb` (and only on first-time scan for a
     * given book). The setter contract isn't exercised here.
     */
    private class StubPlaybackSettings : PlaybackSettings {
        override val rewindSeconds: Flow<Int> = flowOf(10)
        override val forwardSeconds: Flow<Int> = flowOf(30)
        override val autoRewindSeconds: Flow<Int> = flowOf(2)
        override val defaultSpeed: Flow<Float> = flowOf(1.0f)
        override val defaultSkipSilence: Flow<Boolean> = flowOf(false)
        override val defaultGainDb: Flow<Float> = flowOf(0.0f)

        override suspend fun setRewindSeconds(seconds: Int) = Unit
        override suspend fun setForwardSeconds(seconds: Int) = Unit
        override suspend fun setAutoRewindSeconds(seconds: Int) = Unit
        override suspend fun setDefaultSpeed(speed: Float) = Unit
        override suspend fun setDefaultSkipSilence(enabled: Boolean) = Unit
        override suspend fun setDefaultGainDb(db: Float) = Unit
    }
}
