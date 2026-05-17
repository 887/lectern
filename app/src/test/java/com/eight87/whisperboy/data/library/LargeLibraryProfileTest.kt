package com.eight87.whisperboy.data.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Phase P.5 — large-library profiling micro-benchmark.
 *
 * Voice #3175 reports OOM / multi-second hangs on the library screen when the user has hundreds
 * of books and tens of thousands of chapters. This test seeds an in-memory Room database with
 * 500 books + 10 000 chapters (Voice's reported pain threshold), times the queries the library
 * screen actually issues, and FAILS if any exceeds a target threshold. Failure is the signal
 * that `androidx.paging.Pager` is needed on the library list; passing means whisperboy stays
 * smooth without it.
 *
 * **Methodology caveats — the numbers here are *lower bounds*, not real-device measurements.**
 * Robolectric runs Room against an in-memory SQLite on the JVM, so:
 *
 * - No disk I/O: skips the dominant cost on real Android (Voice's #3175 root cause).
 * - JVM JIT vs. ART AOT: warm-up curves differ; a fast JVM run can still translate to a
 *   slower device run.
 * - No main-thread scheduling: the real library screen collects on the main thread; this
 *   test runs everything on `Dispatchers.Default`.
 *
 * The order-of-magnitude signal is still reliable: if first-emission of 500 books exceeds
 * 200 ms here, the real-device number will be worse, and Pager is mandatory. If we land
 * comfortably under threshold, real devices have headroom for the disk multiplier.
 *
 * Thresholds (all assertions; test FAILS if exceeded):
 * - `BookDao.observeActive()` first emission: < 200 ms
 * - `ChapterDao.observeForBook(bookId)` first emission: < 50 ms
 * - `BookDao.observeByAuthor(authorName)` first emission: < 100 ms
 * - Bulk insert (one-time seed cost, documented not gated): < 5 000 ms
 * - Multi-emission stress (10 successive emissions while inserting between each): < 1 000 ms total
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LargeLibraryProfileTest {

    private lateinit var db: LibraryDatabase
    private lateinit var bookDao: BookDao
    private lateinit var chapterDao: ChapterDao

    private val bookCount = 500
    private val chapterCount = 10_000
    private val chaptersPerBook = chapterCount / bookCount // 20

    // ~20 distinct authors → ~25 books per author. R.F.9's per-author filter target.
    private val authorPool: List<String> = (0 until 20).map { "Author $it" }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries() // permitted in JVM tests; mirrors prod is not the goal here
            .build()
        bookDao = db.bookDao()
        chapterDao = db.chapterDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun profile_500_books_10k_chapters() = runBlocking {
        // ---------------------------------------------------------------------
        // 1) Bulk insert: 500 books + 10 000 chapters
        // ---------------------------------------------------------------------
        val rng = Random(seed = 0xB00C)
        val books: List<BookEntity> = (0 until bookCount).map { i ->
            BookEntity(
                bookId = "book-$i",
                treeUriString = "content://tree/$i",
                relativePath = "path/book-$i.m4b",
                title = "Title $i",
                author = authorPool[i % authorPool.size],
                durationMs = 3_600_000L + i * 1_000L,
            )
        }
        val chapters: List<ChapterEntity> = (0 until bookCount).flatMap { b ->
            (0 until chaptersPerBook).map { c ->
                val global = b * chaptersPerBook + c
                ChapterEntity(
                    chapterId = "ch-$global",
                    bookId = "book-$b",
                    chapterIndex = c,
                    title = "Chapter $c",
                    durationMs = 180_000L,
                    positionInBookMs = c * 180_000L,
                )
            }
        }
        val insertMs = measureTimeMillis {
            bookDao.upsertAll(books)
            chapterDao.upsertAll(chapters)
        }
        println("[P.5] bulk insert: ${books.size} books + ${chapters.size} chapters in $insertMs ms")
        assertTrue(
            "insert elapsed=$insertMs ms exceeds threshold 5000 ms",
            insertMs < 5_000,
        )

        // ---------------------------------------------------------------------
        // 2) BookDao.observeActive() first emission — the library screen
        // ---------------------------------------------------------------------
        val libraryMs = measureTimeMillis {
            val rows = withTimeout(5_000) { bookDao.observeActive().first() }
            assertEquals(bookCount, rows.size)
        }
        println("[P.5] library first emission: $libraryMs ms")
        assertTrue(
            "library observeActive first emission elapsed=$libraryMs ms exceeds threshold 200 ms",
            libraryMs < 200,
        )

        // ---------------------------------------------------------------------
        // 3) ChapterDao.observeForBook(bookId) random-book first emission
        // ---------------------------------------------------------------------
        val randomBookId = "book-${rng.nextInt(bookCount)}"
        val chapterMs = measureTimeMillis {
            val rows = withTimeout(5_000) { chapterDao.observeForBook(randomBookId).first() }
            assertEquals(chaptersPerBook, rows.size)
        }
        println("[P.5] per-book chapter first emission ($randomBookId): $chapterMs ms")
        assertTrue(
            "chapter observeForBook first emission elapsed=$chapterMs ms exceeds threshold 50 ms",
            chapterMs < 50,
        )

        // ---------------------------------------------------------------------
        // 4) BookDao.observeByAuthor(author) — R.F.9 per-author filter
        // ---------------------------------------------------------------------
        val targetAuthor = authorPool[7] // ~25 books for this author
        val authorMs = measureTimeMillis {
            val rows = withTimeout(5_000) { bookDao.observeByAuthor(targetAuthor).first() }
            assertEquals(bookCount / authorPool.size, rows.size)
        }
        println("[P.5] per-author filter first emission ($targetAuthor): $authorMs ms")
        assertTrue(
            "observeByAuthor first emission elapsed=$authorMs ms exceeds threshold 100 ms",
            authorMs < 100,
        )

        // ---------------------------------------------------------------------
        // 5) Multi-emission stress — 10 emissions while inserting between each
        // ---------------------------------------------------------------------
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val emissions = mutableListOf<Int>()
            val stressMs = measureTimeMillis {
                withTimeout(10_000) {
                    val collector = scope.launch {
                        bookDao.observeActive().collect { rows ->
                            emissions += rows.size
                            if (emissions.size >= 10) cancel()
                        }
                    }
                    // While the collector runs, insert one new book at a time.
                    var extra = 0
                    while (emissions.size < 10) {
                        bookDao.upsert(
                            BookEntity(
                                bookId = "book-stress-$extra",
                                treeUriString = "content://tree/stress/$extra",
                                relativePath = "stress/$extra.m4b",
                                title = "Stress $extra",
                                author = authorPool[extra % authorPool.size],
                                durationMs = 60_000L,
                            ),
                        )
                        extra++
                        delay(5) // let the observer turn the crank
                    }
                    collector.join()
                }
            }
            println("[P.5] multi-emission stress (10 emissions, inserts between): $stressMs ms; sizes=$emissions")
            assertTrue(
                "multi-emission stress elapsed=$stressMs ms exceeds threshold 1000 ms",
                stressMs < 1_000,
            )
        } finally {
            scope.cancel()
        }
    }
}
