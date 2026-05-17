package com.eight87.whisperboy.data.library.parser

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [MatroskaChapterParser]. Hand-rolls a tiny EBML fixture containing only
 * Segment → Chapters → EditionEntry → 2× ChapterAtom.
 */
class MatroskaChapterParserTest {

    @Test
    fun `parses Matroska Segment Chapters with two chapters`() {
        val bytes = buildMatroskaFixture(
            listOf(
                0L to "Prologue",
                15_000L to "Chapter One",
            )
        )
        val marks = MatroskaChapterParser(ByteArraySeekableSource(bytes)).parse()
        assertEquals(2, marks.size)
        assertEquals(0L, marks[0].positionMs)
        assertEquals("Prologue", marks[0].title)
        assertEquals(15_000L, marks[1].positionMs)
        assertEquals("Chapter One", marks[1].title)
    }

    @Test
    fun `returns empty when no Chapters element present`() {
        val segment = ebmlElement(idBytes(0x18538067, 4), ByteArray(0))
        val marks = MatroskaChapterParser(ByteArraySeekableSource(segment)).parse()
        assertEquals(0, marks.size)
    }

    @Test
    fun `returns empty when no Segment element present`() {
        // Random non-EBML bytes shorter than even one element header — must not crash.
        val marks = MatroskaChapterParser(ByteArraySeekableSource(ByteArray(0))).parse()
        assertEquals(0, marks.size)
    }

    @Test
    fun `chapter atom with no ChapterDisplay yields empty title`() {
        val timeNs = 7_500L * 1_000_000L
        val timeElem = ebmlElement(idBytes(0x91, 1), encodeUint(timeNs))
        val atomBody = timeElem
        val atom = ebmlElement(idBytes(0xB6, 1), atomBody)
        val edition = ebmlElement(idBytes(0x45B9, 2), atom)
        val chaptersElem = ebmlElement(idBytes(0x1043A770, 4), edition)
        val segment = ebmlElement(idBytes(0x18538067, 4), chaptersElem)
        val marks = MatroskaChapterParser(ByteArraySeekableSource(segment)).parse()
        assertEquals(1, marks.size)
        assertEquals(7_500L, marks[0].positionMs)
        assertEquals("", marks[0].title)
    }

    private fun buildMatroskaFixture(chapters: List<Pair<Long, String>>): ByteArray {
        val atoms = ByteArrayOutputStream()
        for ((ns_input, title) in chapters) {
            val timeNs = ns_input * 1_000_000L
            val timeElem = ebmlElement(idBytes(0x91, 1), encodeUint(timeNs))
            val chapStringElem = ebmlElement(idBytes(0x85, 1), title.toByteArray(Charsets.UTF_8))
            val chapDisplayElem = ebmlElement(idBytes(0x80, 1), chapStringElem)
            val atomBody = timeElem + chapDisplayElem
            atoms.write(ebmlElement(idBytes(0xB6, 1), atomBody))
        }
        val edition = ebmlElement(idBytes(0x45B9, 2), atoms.toByteArray())
        val chaptersElem = ebmlElement(idBytes(0x1043A770, 4), edition)
        val segment = ebmlElement(idBytes(0x18538067, 4), chaptersElem)
        return segment
    }

    /** Encode an EBML element ID as a byte array, preserving the length-marker bits. */
    private fun idBytes(id: Long, length: Int): ByteArray =
        ByteArray(length).also { b ->
            for (i in 0 until length) b[i] = ((id ushr ((length - 1 - i) * 8)) and 0xFF).toByte()
        }

    /** Wrap [data] with an EBML header: [id][size][data] where size uses 1- or 2-byte vint. */
    private fun ebmlElement(idBytes: ByteArray, data: ByteArray): ByteArray {
        val sizeBytes = encodeVintSize(data.size.toLong())
        return idBytes + sizeBytes + data
    }

    private fun encodeVintSize(size: Long): ByteArray {
        // 1-byte: 0x80 | size (size <= 0x7F)
        // 2-byte: 0x40 | high, then low (size <= 0x3FFF)
        return when {
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
    }

    /** Big-endian uint encoding, smallest length that holds the value (min 1 byte). */
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

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val out = ByteArray(size + other.size)
        System.arraycopy(this, 0, out, 0, size)
        System.arraycopy(other, 0, out, size, other.size)
        return out
    }
}
