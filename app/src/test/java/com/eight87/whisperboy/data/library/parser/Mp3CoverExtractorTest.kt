package com.eight87.whisperboy.data.library.parser

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [Mp3CoverExtractor]. Hand-rolls an ID3v2.3 tag containing exactly one APIC
 * frame so the parser walks through both the syncsafe tag size and a plain BE32 frame size.
 */
class Mp3CoverExtractorTest {

    @Test
    fun `extracts image bytes from v23 APIC frame`() {
        val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x42, 0x00, 0x99.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val bytes = buildId3v23WithApic(mime = "image/jpeg", description = "Cover", image = image)
        val extracted = Mp3CoverExtractor(ByteArraySeekableSource(bytes)).extract()
        assertArrayEquals(image, extracted)
    }

    @Test
    fun `extracts image bytes from v24 APIC frame with syncsafe size`() {
        val image = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)
        val bytes = buildId3v24WithApic(mime = "image/png", description = "", image = image)
        val extracted = Mp3CoverExtractor(ByteArraySeekableSource(bytes)).extract()
        assertArrayEquals(image, extracted)
    }

    @Test
    fun `returns null for file without ID3v2 header`() {
        val raw = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x44) + ByteArray(100)
        assertNull(Mp3CoverExtractor(ByteArraySeekableSource(raw)).extract())
    }

    private fun buildId3v23WithApic(mime: String, description: String, image: ByteArray): ByteArray {
        val frameBody = ByteArrayOutputStream().apply {
            write(0) // text encoding: ISO-8859-1
            write(mime.toByteArray(Charsets.US_ASCII))
            write(0) // MIME terminator
            write(0x03) // picture type: front cover
            write(description.toByteArray(Charsets.US_ASCII))
            write(0) // description terminator
            write(image)
        }.toByteArray()
        val frame = ByteArrayOutputStream().apply {
            write("APIC".toByteArray(Charsets.US_ASCII))
            write(u32BE(frameBody.size.toLong())) // v2.3 non-syncsafe size
            write(byteArrayOf(0, 0)) // frame flags
            write(frameBody)
        }.toByteArray()
        return id3Header(major = 3, body = frame) + frame
    }

    private fun buildId3v24WithApic(mime: String, description: String, image: ByteArray): ByteArray {
        val frameBody = ByteArrayOutputStream().apply {
            write(0)
            write(mime.toByteArray(Charsets.US_ASCII))
            write(0)
            write(0x03)
            write(description.toByteArray(Charsets.US_ASCII))
            write(0)
            write(image)
        }.toByteArray()
        val frame = ByteArrayOutputStream().apply {
            write("APIC".toByteArray(Charsets.US_ASCII))
            write(syncsafe32(frameBody.size.toLong())) // v2.4 syncsafe size
            write(byteArrayOf(0, 0))
            write(frameBody)
        }.toByteArray()
        return id3Header(major = 4, body = frame) + frame
    }

    private fun id3Header(major: Int, body: ByteArray): ByteArray {
        val header = ByteArrayOutputStream()
        header.write("ID3".toByteArray(Charsets.US_ASCII))
        header.write(major)
        header.write(0) // revision
        header.write(0) // flags
        header.write(syncsafe32(body.size.toLong()))
        return header.toByteArray()
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
