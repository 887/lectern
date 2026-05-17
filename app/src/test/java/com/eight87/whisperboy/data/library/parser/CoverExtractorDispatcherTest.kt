package com.eight87.whisperboy.data.library.parser

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [CoverExtractorDispatcher]'s container-routing logic.
 *
 * The dispatcher's public constructor only takes a [android.content.Context] and instantiates
 * the per-container extractors internally based on a MIME hint / extension / magic-byte sniff.
 * We exercise each branch end-to-end with the smallest valid synthetic byte fixture for that
 * container — same pattern as [ChapterParserDispatcherTest]. If routing picks the wrong backend
 * the fixture won't parse and the assertion fails.
 *
 * Per-format byte details are covered in [Mp3CoverExtractorTest] / [Mp4CoverExtractorTest] /
 * [MatroskaCoverExtractorTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoverExtractorDispatcherTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dispatcher get() = CoverExtractorDispatcher(context)

    private val pngBytes = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x10, 0x20, 0x30,
    )
    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x11, 0x22, 0xFF.toByte(), 0xD9.toByte())

    @Test
    fun `audio mpeg mime routes to MP3 backend`() {
        val uri = writeFile("a.bin", buildId3v23WithApic("image/jpeg", jpegBytes))
        val bytes = runBlocking { dispatcher.extract(uri, mimeType = "audio/mpeg", fileName = null) }
        assertArrayEquals(jpegBytes, bytes)
    }

    @Test
    fun `audio mp4 mime routes to MP4 backend`() {
        val uri = writeFile("a.bin", buildMp4WithCover(jpegBytes))
        val bytes = runBlocking { dispatcher.extract(uri, mimeType = "audio/mp4", fileName = null) }
        assertArrayEquals(jpegBytes, bytes)
    }

    @Test
    fun `m4a extension routes to MP4 backend`() {
        val uri = writeFile("song.m4a", buildMp4WithCover(jpegBytes))
        val bytes = runBlocking { dispatcher.extract(uri, mimeType = null, fileName = "song.m4a") }
        assertArrayEquals(jpegBytes, bytes)
    }

    @Test
    fun `m4b extension routes to MP4 backend`() {
        val uri = writeFile("book.m4b", buildMp4WithCover(jpegBytes))
        val bytes = runBlocking { dispatcher.extract(uri, mimeType = null, fileName = "book.m4b") }
        assertArrayEquals(jpegBytes, bytes)
    }

    @Test
    fun `audio matroska mime routes to Matroska backend`() {
        val uri = writeFile("a.bin", buildMkvWithAttachment("image/png", pngBytes))
        val bytes = runBlocking { dispatcher.extract(uri, mimeType = "audio/matroska", fileName = null) }
        assertArrayEquals(pngBytes, bytes)
    }

    @Test
    fun `mka extension routes to Matroska backend`() {
        val uri = writeFile("a.mka", buildMkvWithAttachment("image/png", pngBytes))
        val bytes = runBlocking { dispatcher.extract(uri, mimeType = null, fileName = "a.mka") }
        assertArrayEquals(pngBytes, bytes)
    }

    @Test
    fun `mkv extension routes to Matroska backend`() {
        val uri = writeFile("a.mkv", buildMkvWithAttachment("image/jpeg", jpegBytes))
        val bytes = runBlocking { dispatcher.extract(uri, mimeType = null, fileName = "a.mkv") }
        assertArrayEquals(jpegBytes, bytes)
    }

    @Test
    fun `webm extension routes to Matroska backend`() {
        val uri = writeFile("a.webm", buildMkvWithAttachment("image/png", pngBytes))
        val bytes = runBlocking { dispatcher.extract(uri, mimeType = "video/webm", fileName = "a.webm") }
        assertArrayEquals(pngBytes, bytes)
    }

    @Test
    fun `unknown mime and unknown extension yields null`() {
        // Pad past 8 bytes so the magic sniffer actually runs (header check needs >= 8).
        val uri = writeFile("mystery.xyz", ByteArray(64) { 0x00 })
        val bytes = runBlocking { dispatcher.extract(uri, mimeType = "application/unknown", fileName = "mystery.xyz") }
        assertNull(bytes)
    }

    @Test
    fun `missing file returns null and does not throw`() {
        val uri = Uri.parse("file:///tmp/whisperboy-cover-does-not-exist-${System.nanoTime()}.m4b")
        val bytes = runBlocking { dispatcher.extract(uri, mimeType = "audio/mp4", fileName = "x.m4b") }
        assertNull(bytes)
    }

    // --- fixture writers ---

    private fun writeFile(name: String, bytes: ByteArray): Uri {
        val f = tempFolder.newFile(name)
        f.writeBytes(bytes)
        return Uri.fromFile(f)
    }

    // --- ID3v2.3 APIC fixture (mirrors Mp3CoverExtractorTest helper) ---

    private fun buildId3v23WithApic(mime: String, image: ByteArray): ByteArray {
        val frameBody = ByteArrayOutputStream().apply {
            write(0) // text encoding ISO-8859-1
            write(mime.toByteArray(Charsets.US_ASCII))
            write(0) // MIME terminator
            write(0x03) // picture type
            write(0) // empty description terminator
            write(image)
        }.toByteArray()
        val frame = ByteArrayOutputStream().apply {
            write("APIC".toByteArray(Charsets.US_ASCII))
            write(u32BE(frameBody.size.toLong()))
            write(byteArrayOf(0, 0))
            write(frameBody)
        }.toByteArray()
        val header = ByteArrayOutputStream().apply {
            write("ID3".toByteArray(Charsets.US_ASCII))
            write(3); write(0); write(0)
            write(syncsafe32(frame.size.toLong()))
        }.toByteArray()
        return header + frame
    }

    // --- MP4 covr fixture ---

    private fun buildMp4WithCover(image: ByteArray): ByteArray {
        val dataBody = ByteArrayOutputStream().apply {
            write(u32BE(0x0DL)) // type-flags: JPEG
            write(u32BE(0L))
            write(image)
        }.toByteArray()
        val data = mp4Box("data", dataBody)
        val covr = mp4Box("covr", data)
        val ilst = mp4Box("ilst", covr)
        val metaBody = byteArrayOf(0, 0, 0, 0) + ilst
        val meta = mp4Box("meta", metaBody)
        val udta = mp4Box("udta", meta)
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

    // --- Matroska attachment fixture ---

    private fun buildMkvWithAttachment(mime: String, image: ByteArray): ByteArray {
        val mimeElem = ebmlElement(idBytes(0x4660, 2), mime.toByteArray(Charsets.US_ASCII))
        val dataElem = ebmlElement(idBytes(0x465C, 2), image)
        val attachedFile = ebmlElement(idBytes(0x61A7, 2), mimeElem + dataElem)
        val attachments = ebmlElement(idBytes(0x1941A469L, 4), attachedFile)
        return ebmlElement(idBytes(0x18538067L, 4), attachments)
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
        size <= 0x0FFFFFFFL -> byteArrayOf(
            (0x10 or ((size ushr 24) and 0x0F).toInt()).toByte(),
            ((size ushr 16) and 0xFF).toByte(),
            ((size ushr 8) and 0xFF).toByte(),
            (size and 0xFF).toByte(),
        )
        else -> error("size too large for fixture: $size")
    }

    private fun u32BE(v: Long): ByteArray = byteArrayOf(
        ((v ushr 24) and 0xFF).toByte(),
        ((v ushr 16) and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte(),
    )

    private fun syncsafe32(v: Long): ByteArray = byteArrayOf(
        ((v ushr 21) and 0x7F).toByte(),
        ((v ushr 14) and 0x7F).toByte(),
        ((v ushr 7) and 0x7F).toByte(),
        (v and 0x7F).toByte(),
    )

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val out = ByteArray(size + other.size)
        System.arraycopy(this, 0, out, 0, size)
        System.arraycopy(other, 0, out, size, other.size)
        return out
    }
}
