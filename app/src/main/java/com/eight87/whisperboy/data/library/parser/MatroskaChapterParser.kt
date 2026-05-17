package com.eight87.whisperboy.data.library.parser

import java.io.EOFException

/**
 * EBML / Matroska chapter parser. Phase I.5.
 *
 * Matroska files (`.mkv`, `.mka`, `.webm`) are EBML. Chapters live at:
 *
 * ```
 * Segment (0x18538067)
 *   Chapters (0x1043A770)
 *     EditionEntry (0x45B9)
 *       ChapterAtom (0xB6)
 *         ChapterTimeStart (0x91)     uint, nanoseconds
 *         ChapterDisplay (0x80)
 *           ChapString (0x85)         UTF-8
 * ```
 *
 * v1 skips ordered editions + nested ChapterAtom hierarchies — first edition + flat atom
 * list is the common case.
 */
internal class MatroskaChapterParser(private val source: SeekableSource) {

    fun parse(): List<ChapterMark> {
        val (segStart, segEnd) = findSegmentBounds() ?: return emptyList()
        val chapters = findElementInRange(segStart, segEnd, ID_CHAPTERS) ?: return emptyList()
        return decodeChapters(chapters.first, chapters.second)
    }

    private fun findSegmentBounds(): Pair<Long, Long>? {
        var pos = 0L
        val len = source.length
        while (pos + 2 <= len) {
            source.seek(pos)
            val id = readEbmlId(source) ?: return null
            val sz = readEbmlSize(source) ?: return null
            val dataStart = source.position()
            val dataEnd = if (sz < 0) len else (dataStart + sz).coerceAtMost(len)
            if (id == ID_SEGMENT) return dataStart to dataEnd
            pos = dataEnd
        }
        return null
    }

    /** Linear scan within [start, end) for the first occurrence of [targetId]. Returns (dataStart, dataEnd). */
    private fun findElementInRange(start: Long, end: Long, targetId: Long): Pair<Long, Long>? {
        var pos = start
        while (pos + 2 <= end) {
            source.seek(pos)
            val id = readEbmlId(source) ?: return null
            val sz = readEbmlSize(source) ?: return null
            val dataStart = source.position()
            val dataEnd = if (sz < 0) end else (dataStart + sz).coerceAtMost(end)
            if (id == targetId) return dataStart to dataEnd
            pos = dataEnd
        }
        return null
    }

    private fun decodeChapters(start: Long, end: Long): List<ChapterMark> {
        val edition = findElementInRange(start, end, ID_EDITION_ENTRY) ?: return emptyList()
        val out = mutableListOf<ChapterMark>()
        var pos = edition.first
        val editionEnd = edition.second
        while (pos + 2 <= editionEnd) {
            source.seek(pos)
            val id = readEbmlId(source) ?: break
            val sz = readEbmlSize(source) ?: break
            val dataStart = source.position()
            val dataEnd = if (sz < 0) editionEnd else (dataStart + sz).coerceAtMost(editionEnd)
            if (id == ID_CHAPTER_ATOM) {
                decodeChapterAtom(dataStart, dataEnd)?.let { out += it }
            }
            pos = dataEnd
        }
        return out
    }

    private fun decodeChapterAtom(start: Long, end: Long): ChapterMark? {
        var timeNs: Long? = null
        var title: String? = null
        var pos = start
        while (pos + 2 <= end) {
            source.seek(pos)
            val id = readEbmlId(source) ?: break
            val sz = readEbmlSize(source) ?: break
            val dataStart = source.position()
            val dataEnd = if (sz < 0) end else (dataStart + sz).coerceAtMost(end)
            when (id) {
                ID_CHAPTER_TIME_START -> timeNs = readUintData(dataStart, dataEnd)
                ID_CHAPTER_DISPLAY -> if (title == null) title = readChapString(dataStart, dataEnd)
            }
            pos = dataEnd
        }
        val t = timeNs ?: return null
        return ChapterMark(positionMs = t / 1_000_000L, title = title ?: "")
    }

    private fun readChapString(start: Long, end: Long): String? {
        val cs = findElementInRange(start, end, ID_CHAP_STRING) ?: return null
        val len = (cs.second - cs.first).toInt()
        if (len <= 0) return ""
        val buf = ByteArray(len)
        source.seek(cs.first)
        source.readFully(buf, 0, len)
        return String(buf, Charsets.UTF_8)
    }

    private fun readUintData(start: Long, end: Long): Long {
        val len = (end - start).toInt().coerceIn(0, 8)
        source.seek(start)
        var v = 0L
        for (i in 0 until len) v = (v shl 8) or (source.readByte().toLong() and 0xFF)
        return v
    }

    companion object {
        const val ID_SEGMENT: Long = 0x18538067L
        const val ID_CHAPTERS: Long = 0x1043A770L
        const val ID_EDITION_ENTRY: Long = 0x45B9L
        const val ID_CHAPTER_ATOM: Long = 0xB6L
        const val ID_CHAPTER_TIME_START: Long = 0x91L
        const val ID_CHAPTER_DISPLAY: Long = 0x80L
        const val ID_CHAP_STRING: Long = 0x85L
    }
}

/**
 * Read an EBML element ID, returning the raw value with length-marker bits included
 * (canonical quoted form, e.g. Segment = 0x18538067). Returns null on EOF.
 */
internal fun readEbmlId(s: SeekableSource): Long? {
    val one = ByteArray(1)
    val n = s.read(one, 0, 1)
    if (n < 0) return null
    val b0 = one[0].toInt() and 0xFF
    val length = when {
        b0 and 0x80 != 0 -> 1
        b0 and 0x40 != 0 -> 2
        b0 and 0x20 != 0 -> 3
        b0 and 0x10 != 0 -> 4
        else -> return null
    }
    var id = b0.toLong()
    if (length > 1) {
        val rest = ByteArray(length - 1)
        try {
            s.readFully(rest, 0, length - 1)
        } catch (_: EOFException) {
            return null
        }
        for (i in rest.indices) id = (id shl 8) or (rest[i].toLong() and 0xFF)
    }
    return id
}

/**
 * Read an EBML variable-size integer (element data size). Returns the size as a positive
 * long, or -1L for the all-ones "unknown size" sentinel.
 */
internal fun readEbmlSize(s: SeekableSource): Long? {
    val buf = ByteArray(1)
    val n = s.read(buf, 0, 1)
    if (n < 0) return null
    val b0 = buf[0].toInt() and 0xFF
    val length = when {
        b0 and 0x80 != 0 -> 1
        b0 and 0x40 != 0 -> 2
        b0 and 0x20 != 0 -> 3
        b0 and 0x10 != 0 -> 4
        b0 and 0x08 != 0 -> 5
        b0 and 0x04 != 0 -> 6
        b0 and 0x02 != 0 -> 7
        b0 and 0x01 != 0 -> 8
        else -> return null
    }
    val mask = (1 shl (8 - length)) - 1
    var v = (b0 and mask).toLong()
    if (length > 1) {
        val rest = ByteArray(length - 1)
        try {
            s.readFully(rest, 0, length - 1)
        } catch (_: EOFException) {
            return null
        }
        for (i in rest.indices) v = (v shl 8) or (rest[i].toLong() and 0xFF)
    }
    val maxForLen = when (length) {
        1 -> 0x7FL
        2 -> 0x3FFFL
        3 -> 0x1FFFFFL
        4 -> 0x0FFFFFFFL
        5 -> 0x07FFFFFFFFL
        6 -> 0x03FFFFFFFFFFL
        7 -> 0x01FFFFFFFFFFFFL
        8 -> 0x00FFFFFFFFFFFFFFL
        else -> 0L
    }
    return if (v == maxForLen) -1L else v
}
