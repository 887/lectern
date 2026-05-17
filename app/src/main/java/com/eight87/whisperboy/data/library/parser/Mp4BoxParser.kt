package com.eight87.whisperboy.data.library.parser

/**
 * Visitor-based MP4 atom walker. Phase I.2.
 *
 * MP4 (ISO BMFF) is a tree of boxes. Each box header is:
 *
 * ```
 * [4-byte big-endian size][4-byte type]
 * ```
 *
 * If `size == 1`, an 8-byte extended size follows the type. If `size == 0`, the box extends
 * to end-of-file.
 *
 * Container boxes have child boxes immediately after the header; leaf boxes have type-specific
 * payload. The walker calls [Mp4BoxVisitor.visit] for every box; the visitor's [VisitResult]
 * controls whether to descend into the box's children, skip it, or stop the walk entirely.
 *
 * Container set defined by [CONTAINER_BOXES] — descend by default only when both the type is
 * in that set and the visitor returns [VisitResult.Descend]. This avoids accidentally
 * descending into leaf boxes that happen to start with valid-looking child headers.
 */
internal class Mp4BoxParser(private val source: SeekableSource) {

    /**
     * Walk the box tree from the current position to [endOffset] (exclusive), calling
     * [visitor] for each top-level box. Recursion happens here, not in the visitor, so a
     * visitor can keep state across an entire walk without managing a manual stack.
     */
    fun walk(
        visitor: Mp4BoxVisitor,
        startOffset: Long = 0L,
        endOffset: Long = source.length,
    ): Boolean {
        source.seek(startOffset)
        return walkRange(visitor, startOffset, endOffset, depth = 0)
    }

    /** @return false if the walk was stopped early. */
    private fun walkRange(
        visitor: Mp4BoxVisitor,
        startOffset: Long,
        endOffset: Long,
        depth: Int,
    ): Boolean {
        if (depth > MAX_DEPTH) return true
        var cursor = startOffset
        while (cursor + 8 <= endOffset) {
            source.seek(cursor)
            val sizeRaw = source.readUInt32BE()
            val type = readBoxType(source)
            var headerSize = 8L
            val size: Long = when (sizeRaw) {
                1L -> {
                    headerSize = 16L
                    source.readUInt64BE()
                }
                0L -> endOffset - cursor
                else -> sizeRaw
            }
            if (size < headerSize) return true // malformed
            val boxEnd = cursor + size
            if (boxEnd > endOffset) return true

            val payloadStart = cursor + headerSize
            val payloadEnd = boxEnd
            val result = visitor.visit(type, payloadStart, payloadEnd, source)
            when (result) {
                VisitResult.Stop -> return false
                VisitResult.Descend -> {
                    if (type in CONTAINER_BOXES) {
                        val descendStart = if (type in META_LIKE_BOXES) payloadStart + 4 else payloadStart
                        if (!walkRange(visitor, descendStart, payloadEnd, depth + 1)) return false
                    }
                }
                VisitResult.Skip -> Unit
            }
            cursor = boxEnd
        }
        return true
    }

    private fun readBoxType(source: SeekableSource): String {
        val buf = ByteArray(4)
        source.readFully(buf, 0, 4)
        return String(buf, Charsets.ISO_8859_1)
    }

    companion object {
        /** Containers descended into when the visitor says [VisitResult.Descend]. */
        val CONTAINER_BOXES: Set<String> = setOf(
            "moov", "trak", "mdia", "minf", "stbl",
            "udta", "meta", "ilst", "edts", "tref",
            "moof", "traf", "mvex",
        )

        /**
         * Boxes whose payload starts with a 4-byte version+flags before their child boxes.
         * `meta` is the canonical one (FullBox semantics).
         */
        val META_LIKE_BOXES: Set<String> = setOf("meta")

        /** Sanity cap so a malformed file can't blow the JVM stack. */
        const val MAX_DEPTH: Int = 16
    }
}

/**
 * Visitor handed to [Mp4BoxParser.walk]. Each call is one box; the visitor decides whether
 * to descend (only meaningful for container boxes), skip (don't descend; continue with the
 * next sibling), or stop (abort the entire walk).
 */
internal fun interface Mp4BoxVisitor {
    fun visit(
        boxType: String,
        payloadStart: Long,
        payloadEnd: Long,
        source: SeekableSource,
    ): VisitResult
}

internal enum class VisitResult { Descend, Skip, Stop }
