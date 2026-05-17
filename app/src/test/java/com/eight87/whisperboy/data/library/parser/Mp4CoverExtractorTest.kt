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

    @Test
    fun `multiple data entries inside covr take the first one`() {
        // Some encoders stack a JPEG `data` entry followed by a PNG `data` entry for the same
        // cover. Voice + iTunes both treat the first as canonical; we pin that behaviour.
        val first = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xAA.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val second = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val firstData = box("data", u32BE(0x0DL) + u32BE(0L) + first)   // JPEG
        val secondData = box("data", u32BE(0x0EL) + u32BE(0L) + second) // PNG
        val covr = box("covr", firstData + secondData)
        val ilst = box("ilst", covr)
        val meta = box("meta", byteArrayOf(0, 0, 0, 0) + ilst)
        val udta = box("udta", meta)
        val moov = box("moov", udta)
        val ftyp = box("ftyp", "M4A     isommp42".toByteArray(Charsets.ISO_8859_1))
        val extracted = Mp4CoverExtractor(ByteArraySeekableSource(ftyp + moov)).extract()
        assertArrayEquals(first, extracted)
    }

    @Test
    fun `truncated covr payload returns null gracefully`() {
        // covr says its `data` atom claims more bytes than actually present (size lies). The
        // extractor must bail with null, not throw EOF.
        val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val truncatedData = ByteArrayOutputStream().apply {
            // Lie: claim 64 bytes total, only provide 8 + 8 + 4 = 20.
            write(u32BE(64L))
            write("data".toByteArray(Charsets.ISO_8859_1))
            write(u32BE(0x0DL))
            write(u32BE(0L))
            write(image)
        }.toByteArray()
        val covr = box("covr", truncatedData)
        val ilst = box("ilst", covr)
        val meta = box("meta", byteArrayOf(0, 0, 0, 0) + ilst)
        val udta = box("udta", meta)
        val moov = box("moov", udta)
        val ftyp = box("ftyp", "M4A     isommp42".toByteArray(Charsets.ISO_8859_1))
        assertNull(Mp4CoverExtractor(ByteArraySeekableSource(ftyp + moov)).extract())
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
