package com.eight87.whisperboy.data.library

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.eight87.whisperboy.data.library.parser.ChapterParser
import com.eight87.whisperboy.data.library.parser.CoverExtractorDispatcher
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [LibraryScannerEnrichment] — Phase D.3 roll-up + I.8 single-file expansion.
 *
 * The enrichment unit is the highest-yield testable piece in the library pipeline: it merges
 * structural scan output with per-file metadata, picks the right cover-art source per Phase A
 * doctrine, and expands single-file books with embedded chapter marks into proper chapter rows.
 *
 * [MediaAnalyzer] is faked in-test (it's an interface). [ChapterParser] and
 * [CoverExtractorDispatcher] are concrete classes that walk real bytes; tests that exercise
 * those paths write synthetic byte fixtures to a temp file and route through Robolectric's
 * ContentResolver (file URIs open through the same `openAssetFileDescriptor` pathway as SAF).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryScannerEnrichmentTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    // --- Multi-chapter roll-up ---

    @Test
    fun `multi-chapter book rolls per-chapter durations into book duration and cumulative positions`() = runBlocking {
        val chapters = listOf(
            ScannedChapter(chapterId = "c0", chapterIndex = 0, fileUri = "file:///fake/a.mp3"),
            ScannedChapter(chapterId = "c1", chapterIndex = 1, fileUri = "file:///fake/b.mp3"),
            ScannedChapter(chapterId = "c2", chapterIndex = 2, fileUri = "file:///fake/c.mp3"),
        )
        val book = ScannedBook(
            bookId = "b1",
            treeUriString = "content://tree/x",
            relativePath = "Author/Book",
            title = "Book",
            chapters = chapters,
        )
        val analyzer = MapMediaAnalyzer(
            mapOf(
                "file:///fake/a.mp3" to FileMetadata(durationMs = 10_000L, title = "One"),
                "file:///fake/b.mp3" to FileMetadata(durationMs = 20_000L, title = "Two"),
                "file:///fake/c.mp3" to FileMetadata(durationMs = 5_000L, title = "Three"),
            ),
        )
        val out = LibraryScannerEnrichment(analyzer).enrich(ScanSnapshot(listOf(book)))
        val rolled = out.books.single()
        assertEquals(35_000L, rolled.durationMs)
        assertEquals(listOf(10_000L, 20_000L, 5_000L), rolled.chapters.map { it.durationMs })
        assertEquals(listOf(0L, 10_000L, 30_000L), rolled.chapters.map { it.positionInBookMs })
        assertEquals(listOf("One", "Two", "Three"), rolled.chapters.map { it.title })
    }

    @Test
    fun `book author taken from first non-null per-chapter author`() = runBlocking {
        val chapters = listOf(
            ScannedChapter(chapterId = "c0", chapterIndex = 0, fileUri = "file:///fake/a.mp3"),
            ScannedChapter(chapterId = "c1", chapterIndex = 1, fileUri = "file:///fake/b.mp3"),
            ScannedChapter(chapterId = "c2", chapterIndex = 2, fileUri = "file:///fake/c.mp3"),
        )
        val book = ScannedBook(
            bookId = "b1",
            treeUriString = "content://tree/x",
            relativePath = "Book",
            title = "Book",
            author = null,
            chapters = chapters,
        )
        val analyzer = MapMediaAnalyzer(
            mapOf(
                "file:///fake/a.mp3" to FileMetadata(durationMs = 1L, author = null),
                "file:///fake/b.mp3" to FileMetadata(durationMs = 1L, author = "Real Author"),
                "file:///fake/c.mp3" to FileMetadata(durationMs = 1L, author = "Another Author"),
            ),
        )
        val out = LibraryScannerEnrichment(analyzer).enrich(ScanSnapshot(listOf(book)))
        assertEquals("Real Author", out.books.single().author)
    }

    @Test
    fun `scan-supplied author wins over per-chapter author`() = runBlocking {
        val chapters = listOf(
            ScannedChapter(chapterId = "c0", chapterIndex = 0, fileUri = "file:///fake/a.mp3"),
        )
        val book = ScannedBook(
            bookId = "b1",
            treeUriString = "content://tree/x",
            relativePath = "Book",
            title = "Book",
            author = "Scanner Author",
            chapters = chapters,
        )
        val analyzer = MapMediaAnalyzer(
            mapOf("file:///fake/a.mp3" to FileMetadata(durationMs = 1L, author = "Tag Author")),
        )
        val out = LibraryScannerEnrichment(analyzer).enrich(ScanSnapshot(listOf(book)))
        assertEquals("Scanner Author", out.books.single().author)
    }

    // --- Cover preference order: folder > per-chapter embedded > extractor fallback ---

    @Test
    fun `folder cover bytes win over per-chapter embedded`() = runBlocking {
        val folderBytes = byteArrayOf(1, 2, 3, 4)
        val chapterBytes = byteArrayOf(9, 9, 9, 9)
        val chapters = listOf(
            ScannedChapter(chapterId = "c0", chapterIndex = 0, fileUri = "file:///fake/a.mp3"),
        )
        val book = ScannedBook(
            bookId = "b1",
            treeUriString = "content://tree/x",
            relativePath = "Book",
            title = "Book",
            chapters = chapters,
            embeddedCoverBytes = folderBytes,
        )
        val analyzer = MapMediaAnalyzer(
            mapOf(
                "file:///fake/a.mp3" to FileMetadata(
                    durationMs = 1L,
                    embeddedCoverBytes = chapterBytes,
                ),
            ),
        )
        val out = LibraryScannerEnrichment(analyzer).enrich(ScanSnapshot(listOf(book)))
        assertArrayEquals(folderBytes, out.books.single().embeddedCoverBytes)
    }

    @Test
    fun `per-chapter embedded wins over extractor fallback`() = runBlocking {
        val chapterBytes = byteArrayOf(7, 7, 7, 7)
        // Write an MP4-with-covr fixture so an extractor call would succeed; assert it doesn't run.
        val extractorBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0xFF.toByte(), 0xD9.toByte())
        val mp4Uri = writeMp4WithCover("a.m4b", extractorBytes)
        val chapters = listOf(
            ScannedChapter(chapterId = "c0", chapterIndex = 0, fileUri = mp4Uri.toString()),
        )
        val book = ScannedBook(
            bookId = "b1",
            treeUriString = "content://tree/x",
            relativePath = "a.m4b",
            title = "Book",
            chapters = chapters,
            embeddedCoverBytes = null,
        )
        val analyzer = MapMediaAnalyzer(
            mapOf(mp4Uri.toString() to FileMetadata(durationMs = 1L, embeddedCoverBytes = chapterBytes)),
        )
        val out = LibraryScannerEnrichment(
            analyzer,
            chapterParser = null,
            coverExtractorDispatcher = CoverExtractorDispatcher(context),
        ).enrich(ScanSnapshot(listOf(book)))
        assertArrayEquals(chapterBytes, out.books.single().embeddedCoverBytes)
    }

    @Test
    fun `extractor fallback runs when neither folder nor per-chapter cover present`() = runBlocking {
        val coverBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x05, 0x06, 0xFF.toByte(), 0xD9.toByte())
        val mp4Uri = writeMp4WithCover("book.m4b", coverBytes)
        val chapters = listOf(
            ScannedChapter(chapterId = "c0", chapterIndex = 0, fileUri = mp4Uri.toString()),
        )
        val book = ScannedBook(
            bookId = "b1",
            treeUriString = "content://tree/x",
            relativePath = "book.m4b",
            title = "Book",
            chapters = chapters,
            embeddedCoverBytes = null,
        )
        // Analyzer reports no embedded cover bytes — must force the fallback path.
        val analyzer = MapMediaAnalyzer(
            mapOf(mp4Uri.toString() to FileMetadata(durationMs = 1L, embeddedCoverBytes = null)),
        )
        val out = LibraryScannerEnrichment(
            analyzer,
            chapterParser = null,
            coverExtractorDispatcher = CoverExtractorDispatcher(context),
        ).enrich(ScanSnapshot(listOf(book)))
        assertArrayEquals(coverBytes, out.books.single().embeddedCoverBytes)
    }

    @Test
    fun `cover stays null when no source produces bytes`() = runBlocking {
        val chapters = listOf(
            ScannedChapter(chapterId = "c0", chapterIndex = 0, fileUri = "file:///fake/a.mp3"),
        )
        val book = ScannedBook(
            bookId = "b1",
            treeUriString = "content://tree/x",
            relativePath = "Book",
            title = "Book",
            chapters = chapters,
        )
        val analyzer = MapMediaAnalyzer(
            mapOf("file:///fake/a.mp3" to FileMetadata(durationMs = 1L, embeddedCoverBytes = null)),
        )
        val out = LibraryScannerEnrichment(analyzer).enrich(ScanSnapshot(listOf(book)))
        assertNull(out.books.single().embeddedCoverBytes)
    }

    // --- I.8 single-file chapter expansion ---

    @Test
    fun `single-file book with embedded chapter marks expands to N chapters with delta durations`() = runBlocking {
        // M4B fixture with three chpl marks at 0ms, 30_000ms, 90_000ms.
        val marks = listOf(0L to "Intro", 30_000L to "Two", 90_000L to "Three")
        val fileUri = writeMp4WithChpl("book.m4b", marks)
        val book = ScannedBook(
            bookId = "bookSingle",
            treeUriString = "content://tree/x",
            relativePath = "book.m4b",
            title = "Big Book",
            chapters = listOf(
                ScannedChapter(
                    chapterId = "c0",
                    chapterIndex = 0,
                    fileUri = fileUri.toString(),
                ),
            ),
        )
        // Analyzer reports the file's total duration as 120_000ms.
        val analyzer = MapMediaAnalyzer(
            mapOf(fileUri.toString() to FileMetadata(durationMs = 120_000L)),
        )
        val out = LibraryScannerEnrichment(
            analyzer,
            chapterParser = ChapterParser(context),
        ).enrich(ScanSnapshot(listOf(book)))
        val rolled = out.books.single()
        assertEquals(3, rolled.chapters.size)
        assertEquals(listOf("Intro", "Two", "Three"), rolled.chapters.map { it.title })
        assertEquals(listOf(0L, 30_000L, 90_000L), rolled.chapters.map { it.positionInBookMs })
        // delta durations: 30_000 - 0 = 30_000; 90_000 - 30_000 = 60_000; 120_000 - 90_000 = 30_000.
        assertEquals(listOf(30_000L, 60_000L, 30_000L), rolled.chapters.map { it.durationMs })
        // Book duration equals file duration, NOT the sum of marks-delta (they happen to match here
        // because the marks-driven branch reports the file duration verbatim).
        assertEquals(120_000L, rolled.durationMs)
        // Chapter ids derived from SafLibraryScanner.chapterIdFor so re-scan stability holds.
        assertEquals(SafLibraryScanner.chapterIdFor("bookSingle", 0), rolled.chapters[0].chapterId)
        assertEquals(SafLibraryScanner.chapterIdFor("bookSingle", 2), rolled.chapters[2].chapterId)
    }

    @Test
    fun `marks-driven durations are not overwritten by per-file analyzer duration`() = runBlocking {
        // If the per-file duration leaked through to every expanded chapter, every chapter would
        // get the full 120_000 figure. Pin that it does not — the last chapter's duration must
        // be (total - last mark) = 20_000, not 120_000.
        val marks = listOf(0L to "A", 50_000L to "B", 100_000L to "C")
        val fileUri = writeMp4WithChpl("multi.m4b", marks)
        val book = ScannedBook(
            bookId = "bookMulti",
            treeUriString = "content://tree/x",
            relativePath = "multi.m4b",
            title = "Multi",
            chapters = listOf(
                ScannedChapter(
                    chapterId = "c0",
                    chapterIndex = 0,
                    fileUri = fileUri.toString(),
                ),
            ),
        )
        val analyzer = MapMediaAnalyzer(
            mapOf(fileUri.toString() to FileMetadata(durationMs = 120_000L)),
        )
        val out = LibraryScannerEnrichment(
            analyzer,
            chapterParser = ChapterParser(context),
        ).enrich(ScanSnapshot(listOf(book)))
        val rolled = out.books.single()
        assertEquals(listOf(50_000L, 50_000L, 20_000L), rolled.chapters.map { it.durationMs })
        // Last chapter's duration is NOT 120_000 — proves the per-file duration did not leak.
        assertEquals(20_000L, rolled.chapters.last().durationMs)
    }

    @Test
    fun `single-file book with no embedded marks is left as one chapter`() = runBlocking {
        // Fixture with no chapter atom at all (empty moov).
        val f = tempFolder.newFile("bare.m4b")
        val empty = mp4Box("ftyp", "M4A     isommp42".toByteArray(Charsets.ISO_8859_1)) +
            mp4Box("moov", ByteArray(0))
        f.writeBytes(empty)
        val fileUri = Uri.fromFile(f)
        val book = ScannedBook(
            bookId = "bareBook",
            treeUriString = "content://tree/x",
            relativePath = "bare.m4b",
            title = "Bare",
            chapters = listOf(
                ScannedChapter(
                    chapterId = "c0",
                    chapterIndex = 0,
                    fileUri = fileUri.toString(),
                ),
            ),
        )
        val analyzer = MapMediaAnalyzer(
            mapOf(fileUri.toString() to FileMetadata(durationMs = 60_000L)),
        )
        val out = LibraryScannerEnrichment(
            analyzer,
            chapterParser = ChapterParser(context),
        ).enrich(ScanSnapshot(listOf(book)))
        val rolled = out.books.single()
        assertEquals(1, rolled.chapters.size)
        assertEquals(60_000L, rolled.chapters.single().durationMs)
        assertEquals(60_000L, rolled.durationMs)
    }

    // --- fakes + fixture builders ---

    /**
     * In-memory [MediaAnalyzer] backed by a `Uri.toString() -> FileMetadata` map.
     * Returns null for any URI not present.
     */
    private class MapMediaAnalyzer(private val table: Map<String, FileMetadata>) : MediaAnalyzer {
        override suspend fun extract(fileUri: Uri): FileMetadata? = table[fileUri.toString()]
    }

    private fun writeMp4WithCover(name: String, image: ByteArray): Uri {
        val f = tempFolder.newFile(name)
        val dataBody = u32BE(0x0DL) + u32BE(0L) + image
        val data = mp4Box("data", dataBody)
        val covr = mp4Box("covr", data)
        val ilst = mp4Box("ilst", covr)
        val meta = mp4Box("meta", byteArrayOf(0, 0, 0, 0) + ilst)
        val udta = mp4Box("udta", meta)
        val moov = mp4Box("moov", udta)
        val ftyp = mp4Box("ftyp", "M4A     isommp42".toByteArray(Charsets.ISO_8859_1))
        f.writeBytes(ftyp + moov)
        return Uri.fromFile(f)
    }

    private fun writeMp4WithChpl(name: String, chapters: List<Pair<Long, String>>): Uri {
        val f = tempFolder.newFile(name)
        val body = ByteArrayOutputStream()
        body.write(byteArrayOf(1, 0, 0, 0)) // version=1 + flags
        body.write(0)                        // reserved
        body.write(u32BE(chapters.size.toLong()))
        for ((ms, n) in chapters) {
            val nb = n.toByteArray(Charsets.UTF_8)
            body.write(u64BE(ms * 10_000L))
            body.write(nb.size.coerceAtMost(255))
            body.write(nb)
        }
        val chpl = mp4Box("chpl", body.toByteArray())
        val udta = mp4Box("udta", chpl)
        val moov = mp4Box("moov", udta)
        val ftyp = mp4Box("ftyp", "M4A     isommp42".toByteArray(Charsets.ISO_8859_1))
        f.writeBytes(ftyp + moov)
        return Uri.fromFile(f)
    }

    private fun mp4Box(type: String, payload: ByteArray): ByteArray {
        require(type.length == 4)
        val out = ByteArrayOutputStream()
        out.write(u32BE((payload.size + 8).toLong()))
        out.write(type.toByteArray(Charsets.ISO_8859_1))
        out.write(payload)
        return out.toByteArray()
    }

    private fun u32BE(v: Long): ByteArray = byteArrayOf(
        ((v ushr 24) and 0xFF).toByte(),
        ((v ushr 16) and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte(),
    )

    private fun u64BE(v: Long): ByteArray = ByteArray(8).also { b ->
        for (i in 0 until 8) b[i] = ((v ushr ((7 - i) * 8)) and 0xFF).toByte()
    }

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val out = ByteArray(size + other.size)
        System.arraycopy(this, 0, out, 0, size)
        System.arraycopy(other, 0, out, size, other.size)
        return out
    }
}
