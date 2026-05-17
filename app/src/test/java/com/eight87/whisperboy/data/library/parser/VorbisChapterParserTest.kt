package com.eight87.whisperboy.data.library.parser

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [VorbisChapterParser]. Fixture is a tiny Ogg stream with two pages: page 1
 * carries a dummy "identification" packet (we don't care about its content; the parser only
 * routes the second packet); page 2 carries an OpusTags-style comment block with
 * `CHAPTER001=` / `CHAPTER001NAME=` entries.
 */
class VorbisChapterParserTest {

    @Test
    fun `parses Vorbis-comment CHAPTER entries from an Ogg stream`() {
        val comments = listOf(
            "ARTIST=Unknown",
            "CHAPTER001=00:00:00.000",
            "CHAPTER001NAME=Opening",
            "CHAPTER002=00:00:10.500",
            "CHAPTER002NAME=Middle",
            "CHAPTER003=00:01:00.000",
            "CHAPTER003NAME=End",
        )
        val opusTagsPayload = buildOpusTagsBlock(vendor = "test", comments = comments)
        val identPacket = "OpusHead".toByteArray() + ByteArray(11)
        val bytes = buildOggStream(packets = listOf(identPacket, opusTagsPayload))

        val marks = VorbisChapterParser(ByteArraySeekableSource(bytes)).parse()
        assertEquals(3, marks.size)
        assertEquals(0L, marks[0].positionMs)
        assertEquals("Opening", marks[0].title)
        assertEquals(10_500L, marks[1].positionMs)
        assertEquals("Middle", marks[1].title)
        assertEquals(60_000L, marks[2].positionMs)
        assertEquals("End", marks[2].title)
    }

    @Test
    fun `returns empty for an Ogg stream with no chapter comments`() {
        val opusTags = buildOpusTagsBlock(vendor = "test", comments = listOf("ARTIST=foo", "TITLE=bar"))
        val ident = "OpusHead".toByteArray() + ByteArray(11)
        val bytes = buildOggStream(packets = listOf(ident, opusTags))
        val marks = VorbisChapterParser(ByteArraySeekableSource(bytes)).parse()
        assertEquals(0, marks.size)
    }

    private fun buildOpusTagsBlock(vendor: String, comments: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("OpusTags".toByteArray())
        val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
        out.write(le32(vendorBytes.size))
        out.write(vendorBytes)
        out.write(le32(comments.size))
        for (c in comments) {
            val b = c.toByteArray(Charsets.UTF_8)
            out.write(le32(b.size))
            out.write(b)
        }
        return out.toByteArray()
    }

    private fun le32(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(),
        ((v ushr 16) and 0xFF).toByte(),
        ((v ushr 24) and 0xFF).toByte(),
    )

    /** Build an Ogg bitstream: one page per packet (good enough for tests). */
    private fun buildOggStream(packets: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((idx, packet) in packets.withIndex()) {
            out.write(buildOggPage(packet, headerType = if (idx == 0) 0x02 else 0x00, seq = idx))
        }
        return out.toByteArray()
    }

    private fun buildOggPage(packet: ByteArray, headerType: Int, seq: Int): ByteArray {
        // Build the segment table — packet of size N is encoded as floor(N/255) bytes of 255
        // followed by one byte of (N mod 255). Last byte being < 255 marks end-of-packet.
        val full = packet.size / 255
        val rem = packet.size % 255
        val segTable = ByteArray(full + 1).also { t ->
            for (i in 0 until full) t[i] = 0xFF.toByte()
            t[full] = rem.toByte()
        }
        require(segTable.size <= 255) { "test packets must fit in one Ogg page" }

        val out = ByteArrayOutputStream()
        out.write("OggS".toByteArray())
        out.write(0) // version
        out.write(headerType)
        out.write(ByteArray(8))           // granule position (zero)
        out.write(ByteArray(4))           // serial number
        out.write(le32(seq))               // page sequence number
        out.write(ByteArray(4))           // CRC (parser ignores this)
        out.write(segTable.size)
        out.write(segTable)
        out.write(packet)
        return out.toByteArray()
    }

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val out = ByteArray(size + other.size)
        System.arraycopy(this, 0, out, 0, size)
        System.arraycopy(other, 0, out, size, other.size)
        return out
    }
}
