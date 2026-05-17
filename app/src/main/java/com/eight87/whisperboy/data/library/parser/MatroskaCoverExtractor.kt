package com.eight87.whisperboy.data.library.parser

/**
 * Reads the first `image/X` AttachedFile (any `image/` MIME) from an EBML / Matroska file. Phase A.3.
 *
 * The element path is:
 *
 * ```
 * Segment       (0x18538067)
 *   Attachments (0x1941A469)
 *     AttachedFile (0x61A7)
 *       FileMimeType (0x4660)
 *       FileData     (0x465C)
 *       FileName     (0x466E)   (optional)
 * ```
 *
 * Returns the first `FileData` bytes whose sibling `FileMimeType` starts with `image/`.
 * Mirrors Voice's `MatroskaCoverExtractor.kt`.
 *
 * Reuses [readEbmlId] / [readEbmlSize] from [MatroskaChapterParser] for vint decoding so the
 * two parsers walk EBML with the same rules.
 */
internal class MatroskaCoverExtractor(private val source: SeekableSource) : CoverExtractor {

    override fun extract(): ByteArray? {
        val seg = findSegmentBounds() ?: return null
        val attachments = findElementInRange(seg.first, seg.second, ID_ATTACHMENTS) ?: return null
        var pos = attachments.first
        val end = attachments.second
        while (pos + 2 <= end) {
            source.seek(pos)
            val id = readEbmlId(source) ?: break
            val sz = readEbmlSize(source) ?: break
            val dataStart = source.position()
            val dataEnd = if (sz < 0) end else (dataStart + sz).coerceAtMost(end)
            if (id == ID_ATTACHED_FILE) {
                val bytes = decodeAttachedFile(dataStart, dataEnd)
                if (bytes != null) return bytes
            }
            pos = dataEnd
        }
        return null
    }

    private fun decodeAttachedFile(start: Long, end: Long): ByteArray? {
        var mime: String? = null
        var dataRange: Pair<Long, Long>? = null
        var pos = start
        while (pos + 2 <= end) {
            source.seek(pos)
            val id = readEbmlId(source) ?: break
            val sz = readEbmlSize(source) ?: break
            val dataStart = source.position()
            val dataEnd = if (sz < 0) end else (dataStart + sz).coerceAtMost(end)
            when (id) {
                ID_FILE_MIME_TYPE -> mime = readAsciiString(dataStart, dataEnd)
                ID_FILE_DATA -> if (dataRange == null) dataRange = dataStart to dataEnd
            }
            pos = dataEnd
        }
        val resolvedMime = mime ?: return null
        if (!resolvedMime.startsWith("image/")) return null
        val range = dataRange ?: return null
        val len = (range.second - range.first).toInt()
        if (len <= 0) return null
        val buf = ByteArray(len)
        source.seek(range.first)
        source.readFully(buf, 0, len)
        return buf
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

    private fun readAsciiString(start: Long, end: Long): String {
        val len = (end - start).toInt().coerceAtLeast(0)
        if (len == 0) return ""
        val buf = ByteArray(len)
        source.seek(start)
        source.readFully(buf, 0, len)
        // Strip trailing NUL padding some producers emit.
        var trimmed = len
        while (trimmed > 0 && buf[trimmed - 1] == 0.toByte()) trimmed--
        return String(buf, 0, trimmed, Charsets.US_ASCII)
    }

    companion object {
        const val ID_SEGMENT: Long = 0x18538067L
        const val ID_ATTACHMENTS: Long = 0x1941A469L
        const val ID_ATTACHED_FILE: Long = 0x61A7L
        const val ID_FILE_MIME_TYPE: Long = 0x4660L
        const val ID_FILE_DATA: Long = 0x465CL
    }
}
