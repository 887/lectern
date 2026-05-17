package com.eight87.whisperboy.data.library.parser

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ChapterParser]'s container-routing logic.
 *
 * `ChapterParser.parse(uri, mimeType, fileName)` sniffs the container by mime first, then
 * extension, then by magic bytes. These tests pin each branch by feeding synthetic byte
 * streams via `Uri.fromFile(...)` (Robolectric's ContentResolver opens file URIs through
 * the standard pathway, which goes through `openAssetFileDescriptor` just like SAF URIs).
 *
 * Per-format byte fixtures are minimal — we only need *enough* shape that the format-specific
 * parser returns a recognisable result so we can assert "the right backend ran". Detailed
 * per-format bytes are covered in [Mp4ChapterParserTest] / [MatroskaChapterParserTest] /
 * [VorbisChapterParserTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChapterParserDispatcherTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val parser get() = ChapterParser(context)

    @Test
    fun `mp4 mime routes to Mp4 backend`() {
        val uri = writeMp4WithChpl("a.bin", listOf(0L to "One", 30_000L to "Two"))
        val marks = runBlocking { parser.parse(uri, mimeType = "audio/mp4", fileName = null) }
        assertEquals(2, marks.size)
        assertEquals("One", marks[0].title)
        assertEquals("Two", marks[1].title)
    }

    @Test
    fun `m4b extension routes to Mp4 backend`() {
        val uri = writeMp4WithChpl("book.m4b", listOf(0L to "Intro"))
        val marks = runBlocking { parser.parse(uri, mimeType = null, fileName = "book.m4b") }
        assertEquals(1, marks.size)
        assertEquals("Intro", marks[0].title)
    }

    @Test
    fun `m4a extension routes to Mp4 backend`() {
        val uri = writeMp4WithChpl("song.m4a", listOf(0L to "A"))
        val marks = runBlocking { parser.parse(uri, mimeType = null, fileName = "song.m4a") }
        assertEquals(1, marks.size)
    }

    @Test
    fun `matroska mime routes to Matroska backend`() {
        val uri = writeMatroska("a.bin", listOf(0L to "Prologue", 12_000L to "Chapter One"))
        val marks = runBlocking { parser.parse(uri, mimeType = "audio/matroska", fileName = null) }
        assertEquals(2, marks.size)
        assertEquals("Prologue", marks[0].title)
    }

    @Test
    fun `mkv extension routes to Matroska backend`() {
        val uri = writeMatroska("clip.mkv", listOf(0L to "Start"))
        val marks = runBlocking { parser.parse(uri, mimeType = null, fileName = "clip.mkv") }
        assertEquals(1, marks.size)
        assertEquals("Start", marks[0].title)
    }

    @Test
    fun `webm extension routes to Matroska backend`() {
        val uri = writeMatroska("v.webm", listOf(0L to "W"))
        val marks = runBlocking { parser.parse(uri, mimeType = "video/webm", fileName = "v.webm") }
        assertEquals(1, marks.size)
    }

    @Test
    fun `unknown mime + extension yields empty list (no throw)`() {
        // Write some garbage bytes that don't match any magic.
        val f = tempFolder.newFile("mystery.xyz")
        f.writeBytes(ByteArray(64) { 0x00 })
        val uri = Uri.fromFile(f)
        val marks = runBlocking { parser.parse(uri, mimeType = "application/unknown", fileName = "mystery.xyz") }
        assertEquals(0, marks.size)
    }

    @Test
    fun `extension wins when mime is application octet-stream (generic provider)`() {
        // Some SAF providers (notably Google Drive / some samba shares) return
        // `application/octet-stream` regardless of file type. The dispatcher must
        // fall through to the extension and route correctly.
        val uri = writeMp4WithChpl("book.m4b", listOf(0L to "Intro", 1000L to "Two"))
        val marks = runBlocking {
            parser.parse(uri, mimeType = "application/octet-stream", fileName = "book.m4b")
        }
        assertEquals(2, marks.size)
        assertEquals("Intro", marks[0].title)
    }

    @Test
    fun `mime takes precedence over filename without extension`() {
        // Filename has no extension at all (some content-resolver paths look like that
        // — the SAF document id is opaque). The audio/mp4 mime should route to MP4.
        val uri = writeMp4WithChpl("noextension", listOf(0L to "Only"))
        val marks = runBlocking {
            parser.parse(uri, mimeType = "audio/mp4", fileName = "noextension")
        }
        assertEquals(1, marks.size)
        assertEquals("Only", marks[0].title)
    }

    @Test
    fun `extension matching is case-insensitive (uppercase M4B routes to Mp4)`() {
        // The dispatcher lowercases the extension before matching. Provider-supplied
        // filenames sometimes preserve uppercase from a Windows-origin library.
        val uri = writeMp4WithChpl("LOUD.M4B", listOf(0L to "C"))
        val marks = runBlocking { parser.parse(uri, mimeType = null, fileName = "LOUD.M4B") }
        assertEquals(1, marks.size)
        assertEquals("C", marks[0].title)
    }

    @Test
    fun `mp4 file with no chpl box yields empty list (no throw)`() {
        // A well-formed MP4 with `ftyp + moov` (no `chpl` inside `udta`) — opens
        // cleanly, container detects as Mp4, but the parser finds no chapter
        // headers. Must return empty rather than propagate an exception.
        val f = tempFolder.newFile("nochpl.m4b")
        val ftyp = mp4Box("ftyp", "M4A     isommp42".toByteArray(Charsets.ISO_8859_1))
        // Empty moov (no udta/chpl inside).
        val moov = mp4Box("moov", ByteArray(0))
        f.writeBytes(ftyp + moov)
        val uri = Uri.fromFile(f)
        val marks = runBlocking {
            parser.parse(uri, mimeType = "audio/mp4", fileName = "nochpl.m4b")
        }
        assertEquals(0, marks.size)
    }

    @Test
    fun `dispatcher swallows IO errors and returns empty`() {
        // URI that points at no real file — open should fail; parse must not propagate.
        val uri = Uri.parse("file:///tmp/whisperboy-does-not-exist-${System.nanoTime()}.m4b")
        val marks = runBlocking { parser.parse(uri, mimeType = "audio/mp4", fileName = "x.m4b") }
        assertTrue(marks.isEmpty())
    }

    // --- fixture builders (file-backed wrappers around the byte fixtures in sibling tests) ---

    private fun writeMp4WithChpl(name: String, chapters: List<Pair<Long, String>>): Uri {
        val f = tempFolder.newFile(name)
        f.writeBytes(buildMp4Bytes(chapters))
        return Uri.fromFile(f)
    }

    private fun writeMatroska(name: String, chapters: List<Pair<Long, String>>): Uri {
        val f = tempFolder.newFile(name)
        f.writeBytes(buildMatroskaBytes(chapters))
        return Uri.fromFile(f)
    }

    // --- byte fixture constructors (mirror the helpers in Mp4ChapterParserTest / MatroskaChapterParserTest) ---

    private fun buildMp4Bytes(chapters: List<Pair<Long, String>>): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(byteArrayOf(1, 0, 0, 0))    // version=1 + flags
        body.write(0)                            // reserved
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
        return ftyp + moov
    }

    private fun mp4Box(type: String, payload: ByteArray): ByteArray {
        require(type.length == 4)
        val out = ByteArrayOutputStream()
        out.write(u32BE((payload.size + 8).toLong()))
        out.write(type.toByteArray(Charsets.ISO_8859_1))
        out.write(payload)
        return out.toByteArray()
    }

    private fun buildMatroskaBytes(chapters: List<Pair<Long, String>>): ByteArray {
        val atoms = ByteArrayOutputStream()
        for ((ms, title) in chapters) {
            val timeNs = ms * 1_000_000L
            val timeElem = ebmlElement(idBytes(0x91, 1), encodeUint(timeNs))
            val chapStr = ebmlElement(idBytes(0x85, 1), title.toByteArray(Charsets.UTF_8))
            val chapDisplay = ebmlElement(idBytes(0x80, 1), chapStr)
            atoms.write(ebmlElement(idBytes(0xB6, 1), timeElem + chapDisplay))
        }
        val edition = ebmlElement(idBytes(0x45B9, 2), atoms.toByteArray())
        val chaptersElem = ebmlElement(idBytes(0x1043A770, 4), edition)
        return ebmlElement(idBytes(0x18538067, 4), chaptersElem)
    }

    private fun idBytes(id: Long, length: Int): ByteArray =
        ByteArray(length).also { b ->
            for (i in 0 until length) b[i] = ((id ushr ((length - 1 - i) * 8)) and 0xFF).toByte()
        }

    private fun ebmlElement(idBytes: ByteArray, data: ByteArray): ByteArray =
        idBytes + encodeVintSize(data.size.toLong()) + data

    private fun encodeVintSize(size: Long): ByteArray = when {
        size <= 0x7F -> byteArrayOf((0x80 or size.toInt()).toByte())
        size <= 0x3FFF -> byteArrayOf(
            (0x40 or ((size ushr 8) and 0x3F).toInt()).toByte(),
            (size and 0xFF).toByte(),
        )
        size <= 0x1FFFFF -> byteArrayOf(
            (0x20 or ((size ushr 16) and 0x1F).toInt()).toByte(),
            ((size ushr 8) and 0xFF).toByte(),
            (size and 0xFF).toByte(),
        )
        else -> error("size too large for test fixture: $size")
    }

    private fun encodeUint(v: Long): ByteArray {
        if (v == 0L) return byteArrayOf(0)
        val bytes = mutableListOf<Byte>()
        var n = v
        while (n != 0L) {
            bytes.add(0, (n and 0xFF).toByte())
            n = n ushr 8
        }
        return bytes.toByteArray()
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
