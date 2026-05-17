package com.eight87.whisperboy.data.library.parser

/**
 * Reads the embedded cover from an M4B / M4A / MP4 file's iTunes metadata atom. Phase A.3.
 *
 * The atom path is:
 *
 * ```
 * moov
 *   udta
 *     meta            (FullBox — 4-byte version+flags prefix; handled by Mp4BoxParser)
 *       ilst
 *         covr        (leaf — payload = one or more `data` boxes)
 *           data      ([4 type-flags][4 reserved][image bytes])
 * ```
 *
 * `data` type-flags: 0x0D = JPEG, 0x0E = PNG, 0x1B = BMP. We return the bytes as-is — Coil
 * sniffs the format on decode, no need to record it here.
 *
 * Reuses Phase I's [Mp4BoxParser] visitor walker. The walker descends into `udta`/`meta`/`ilst`
 * via [Mp4BoxParser.CONTAINER_BOXES]; the visitor short-circuits on the first `covr` it sees.
 */
internal class Mp4CoverExtractor(private val source: SeekableSource) : CoverExtractor {

    override fun extract(): ByteArray? {
        var found: ByteArray? = null
        val visitor = Mp4BoxVisitor { boxType, payloadStart, payloadEnd, src ->
            when (boxType) {
                "covr" -> {
                    found = readCovrPayload(src, payloadStart, payloadEnd)
                    VisitResult.Stop
                }
                else -> VisitResult.Descend
            }
        }
        Mp4BoxParser(source).walk(visitor)
        return found
    }

    /**
     * `covr` body is a `data` atom: `[4 size][4 'data'][4 type-flags][4 reserved][image bytes]`.
     * Some producers stack multiple `data` entries (one per image); we take the first.
     */
    private fun readCovrPayload(s: SeekableSource, start: Long, end: Long): ByteArray? {
        if (end - start < 16) return null
        s.seek(start)
        val dataSize = s.readUInt32BE()
        val typeBuf = ByteArray(4)
        s.readFully(typeBuf, 0, 4)
        val type = String(typeBuf, Charsets.ISO_8859_1)
        if (type != "data") return null
        if (dataSize < 16L || start + dataSize > end) return null
        // Skip type-flags(4) + reserved(4).
        s.readUInt32BE()
        s.readUInt32BE()
        val imageLen = (dataSize - 16L).toInt()
        if (imageLen <= 0) return null
        val out = ByteArray(imageLen)
        s.readFully(out, 0, imageLen)
        return out
    }
}
