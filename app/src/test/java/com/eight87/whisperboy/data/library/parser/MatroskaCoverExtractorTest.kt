package com.eight87.whisperboy.data.library.parser

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatroskaCoverExtractorTest {

    @Test
    fun `extracts FileData with image MIME from first AttachedFile`() {
        val image = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02)
        val bytes = buildMkvWithAttachment("image/png", image)
        val extracted = MatroskaCoverExtractor(ByteArraySeekableSource(bytes)).extract()
        assertArrayEquals(image, extracted)
    }

    @Test
    fun `skips non-image attachments`() {
        val bytes = buildMkvWithAttachment("application/x-cue", byteArrayOf(1, 2, 3))
        assertNull(MatroskaCoverExtractor(ByteArraySeekableSource(bytes)).extract())
    }

    @Test
    fun `two attachments first non-image second image - second wins`() {
        // A common shape: subtitle/font/cue attached before the cover image. The extractor
        // must skip the non-image attachment and return the second one's FileData.
        val image = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)

        val mime1 = ebmlElement(idBytes(0x4660, 2), "application/x-cue".toByteArray(Charsets.US_ASCII))
        val data1 = ebmlElement(idBytes(0x465C, 2), byteArrayOf(1, 2, 3, 4))
        val attached1 = ebmlElement(idBytes(0x61A7, 2), mime1 + data1)

        val mime2 = ebmlElement(idBytes(0x4660, 2), "image/png".toByteArray(Charsets.US_ASCII))
        val data2 = ebmlElement(idBytes(0x465C, 2), image)
        val attached2 = ebmlElement(idBytes(0x61A7, 2), mime2 + data2)

        val attachments = ebmlElement(idBytes(0x1941A469L, 4), attached1 + attached2)
        val segment = ebmlElement(idBytes(0x18538067L, 4), attachments)
        val extracted = MatroskaCoverExtractor(ByteArraySeekableSource(segment)).extract()
        assertArrayEquals(image, extracted)
    }

    @Test
    fun `returns null when Attachments element absent`() {
        val segment = ebmlElement(idBytes(0x18538067, 4), ByteArray(0))
        assertNull(MatroskaCoverExtractor(ByteArraySeekableSource(segment)).extract())
    }

    private fun buildMkvWithAttachment(mime: String, image: ByteArray): ByteArray {
        val mimeElem = ebmlElement(idBytes(0x4660, 2), mime.toByteArray(Charsets.US_ASCII))
        val dataElem = ebmlElement(idBytes(0x465C, 2), image)
        val attachedFile = ebmlElement(idBytes(0x61A7, 2), mimeElem + dataElem)
        val attachments = ebmlElement(idBytes(0x1941A469L, 4), attachedFile)
        val segment = ebmlElement(idBytes(0x18538067L, 4), attachments)
        return segment
    }

    private fun idBytes(id: Long, length: Int): ByteArray =
        ByteArray(length).also { b ->
            for (i in 0 until length) b[i] = ((id ushr ((length - 1 - i) * 8)) and 0xFF).toByte()
        }

    private fun ebmlElement(idBytes: ByteArray, data: ByteArray): ByteArray {
        val sizeBytes = encodeVintSize(data.size.toLong())
        return idBytes + sizeBytes + data
    }

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

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val out = ByteArray(size + other.size)
        System.arraycopy(this, 0, out, 0, size)
        System.arraycopy(other, 0, out, size, other.size)
        return out
    }
}
