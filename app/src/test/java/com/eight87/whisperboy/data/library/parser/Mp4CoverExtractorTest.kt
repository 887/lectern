package com.eight87.whisperboy.data.library.parser

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [Mp4CoverExtractor]. Hand-rolls a tiny MP4 fixture with `moov/udta/meta/ilst/covr`.
 */
class Mp4CoverExtractorTest {

    @Test
    fun `extracts JPEG bytes from covr data atom`() {
        val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0x03, 0xFF.toByte(), 0xD9.toByte())
        val bytes = buildMp4WithCover(image, typeFlag = 0x0D)
        val extracted = Mp4CoverExtractor(ByteArraySeekableSource(bytes)).extract()
        assertArrayEquals(image, extracted)
    }

    @Test
    fun `returns null when no covr atom present`() {
        val moov = box("moov", ByteArray(0))
        val ftyp = box("ftyp", "M4A     isommp42".toByteArray(Charsets.ISO_8859_1))
        val bytes = ftyp + moov
        assertNull(Mp4CoverExtractor(ByteArraySeekableSource(bytes)).extract())
    }

    private fun buildMp4WithCover(image: ByteArray, typeFlag: Int): ByteArray {
        // data atom body: [4 type-flags][4 reserved][image]
        val dataBody = ByteArrayOutputStream().apply {
            write(u32BE(typeFlag.toLong()))
            write(u32BE(0L))
            write(image)
        }.toByteArray()
        val dataAtom = box("data", dataBody)
        val covr = box("covr", dataAtom)
        val ilst = box("ilst", covr)
        // meta is a FullBox: 4-byte version+flags prefix before children.
        val metaBody = byteArrayOf(0, 0, 0, 0) + ilst
        val meta = box("meta", metaBody)
        val udta = box("udta", meta)
        val moov = box("moov", udta)
        val ftyp = box("ftyp", "M4A     isommp42".toByteArray(Charsets.ISO_8859_1))
        return ftyp + moov
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        require(type.length == 4)
        val totalSize = payload.size + 8
        val out = ByteArrayOutputStream()
        out.write(u32BE(totalSize.toLong()))
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
}
