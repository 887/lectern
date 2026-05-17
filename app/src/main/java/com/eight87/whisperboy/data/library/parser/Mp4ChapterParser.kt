package com.eight87.whisperboy.data.library.parser

/**
 * Extracts chapter markers from an M4B / M4A / MP4 audiobook. Phase I.3 + I.4.
 *
 * Two parallel chapter conventions exist in the wild:
 *
 * - **Apple chap-track** (I.3): the audio track's `tref/chap` box lists track IDs of text
 *   tracks whose samples carry chapter titles. Sample timing is recovered from `stts` +
 *   `mdhd` timescale; sample data offsets from `stco`/`co64` + `stsc` + `stsz`.
 * - **Nero `chpl`** (I.4): a flat list under `moov/udta/chpl`. Each entry is
 *   `[8-byte 100ns timestamp][1-byte name length][name bytes]`.
 *
 * If both are present, the Apple chap-track wins (it's the richer, modern convention).
 * Falls back to Nero, then to an empty list.
 */
internal class Mp4ChapterParser(private val source: SeekableSource) {

    fun parse(): List<ChapterMark> {
        val collector = Mp4Collector()
        Mp4BoxParser(source).walk(collector)

        // Resolve Apple chap-track first.
        val apple = resolveAppleChapTrack(collector)
        if (apple.isNotEmpty()) return apple

        // Fall back to Nero chpl.
        return collector.neroChapters
    }

    private fun resolveAppleChapTrack(c: Mp4Collector): List<ChapterMark> {
        if (c.chapTrackIds.isEmpty()) return emptyList()
        for (trackId in c.chapTrackIds) {
            val track = c.tracksById[trackId] ?: continue
            val marks = decodeTextSampleTrack(track) ?: continue
            if (marks.isNotEmpty()) return marks
        }
        return emptyList()
    }

    /**
     * Read each sample of a `text`-handler track as `[2-byte length][UTF-8 title]` and pair
     * with the timestamp recovered from `stts` + `mdhd` timescale.
     */
    private fun decodeTextSampleTrack(track: Mp4Track): List<ChapterMark>? {
        val timescale = track.timescale.takeIf { it > 0 } ?: return null
        val sizes = track.sampleSizes ?: return null
        val offsets = computeSampleFileOffsets(track) ?: return null
        val times = computeSampleStartTimes(track) ?: return null
        val count = minOf(sizes.size, offsets.size, times.size)
        if (count == 0) return null

        val out = ArrayList<ChapterMark>(count)
        for (i in 0 until count) {
            val size = sizes[i]
            val offset = offsets[i]
            if (size < 2) {
                out += ChapterMark(positionMs = times[i] * 1000L / timescale, title = "")
                continue
            }
            source.seek(offset)
            val lenHi = source.readByte()
            val lenLo = source.readByte()
            val titleLen = ((lenHi shl 8) or lenLo).coerceAtMost(size - 2)
            val title = if (titleLen > 0) {
                val buf = ByteArray(titleLen)
                source.readFully(buf, 0, titleLen)
                stripBomAndDecode(buf)
            } else ""
            out += ChapterMark(positionMs = times[i] * 1000L / timescale, title = title)
        }
        return out
    }

    private fun stripBomAndDecode(buf: ByteArray): String {
        // Apple text samples are sometimes UTF-16 with BOM. Strip BOM, otherwise UTF-8.
        return when {
            buf.size >= 2 && buf[0] == 0xFE.toByte() && buf[1] == 0xFF.toByte() ->
                String(buf, 2, buf.size - 2, Charsets.UTF_16BE)
            buf.size >= 2 && buf[0] == 0xFF.toByte() && buf[1] == 0xFE.toByte() ->
                String(buf, 2, buf.size - 2, Charsets.UTF_16LE)
            buf.size >= 3 && buf[0] == 0xEF.toByte() && buf[1] == 0xBB.toByte() && buf[2] == 0xBF.toByte() ->
                String(buf, 3, buf.size - 3, Charsets.UTF_8)
            else -> String(buf, Charsets.UTF_8)
        }
    }

    /**
     * Combine `stco`/`co64` (chunk file offsets) + `stsc` (samples-per-chunk) + `stsz`
     * (sample sizes) to produce a per-sample absolute file offset table.
     */
    private fun computeSampleFileOffsets(track: Mp4Track): LongArray? {
        val chunkOffsets = track.chunkOffsets ?: return null
        val sizes = track.sampleSizes ?: return null
        val stsc = track.sampleToChunk ?: return null
        if (chunkOffsets.isEmpty() || sizes.isEmpty() || stsc.isEmpty()) return null

        // Expand stsc into "for chunk i, samplesPerChunk" by walking the entries.
        val totalChunks = chunkOffsets.size
        val samplesInChunk = IntArray(totalChunks)
        for (i in stsc.indices) {
            val firstChunk = stsc[i].firstChunk - 1 // 1-based → 0-based
            val nextFirst = if (i + 1 < stsc.size) stsc[i + 1].firstChunk - 1 else totalChunks
            val spc = stsc[i].samplesPerChunk
            for (c in firstChunk until nextFirst.coerceAtMost(totalChunks)) {
                samplesInChunk[c] = spc
            }
        }

        val out = LongArray(sizes.size)
        var sampleIdx = 0
        for (c in 0 until totalChunks) {
            var offset = chunkOffsets[c]
            val count = samplesInChunk[c]
            for (k in 0 until count) {
                if (sampleIdx >= sizes.size) return out
                out[sampleIdx] = offset
                offset += sizes[sampleIdx]
                sampleIdx++
            }
        }
        return out
    }

    /**
     * Expand `stts` (run-length encoded sample durations) into per-sample start times (in
     * the track's timescale).
     */
    private fun computeSampleStartTimes(track: Mp4Track): LongArray? {
        val stts = track.timeToSample ?: return null
        val total = stts.sumOf { it.sampleCount.toLong() }.toInt()
        val out = LongArray(total)
        var t = 0L
        var idx = 0
        for (entry in stts) {
            for (k in 0 until entry.sampleCount) {
                if (idx >= total) break
                out[idx++] = t
                t += entry.sampleDelta
            }
        }
        return out
    }
}

// ---- collector + intermediate types ----

internal data class StscEntry(val firstChunk: Int, val samplesPerChunk: Int, val sampleDescIndex: Int)
internal data class SttsEntry(val sampleCount: Int, val sampleDelta: Long)

internal class Mp4Track(
    var trackId: Int = 0,
    var handler: String? = null,
    var timescale: Long = 0L,
    var chunkOffsets: LongArray? = null,
    var sampleSizes: IntArray? = null,
    var sampleToChunk: List<StscEntry>? = null,
    var timeToSample: List<SttsEntry>? = null,
)

/**
 * Stateful [Mp4BoxVisitor] that builds a per-track table plus the Nero chapter list and
 * Apple chap-track references in one walk.
 */
internal class Mp4Collector : Mp4BoxVisitor {
    val tracksById: MutableMap<Int, Mp4Track> = LinkedHashMap()
    val chapTrackIds: MutableList<Int> = mutableListOf()
    val neroChapters: MutableList<ChapterMark> = mutableListOf()

    private var currentTrack: Mp4Track? = null

    override fun visit(
        boxType: String,
        payloadStart: Long,
        payloadEnd: Long,
        source: SeekableSource,
    ): VisitResult {
        return when (boxType) {
            "trak" -> {
                currentTrack = Mp4Track().also {
                    // Insert with a placeholder id; tkhd will set the real one.
                    tracksById[System.identityHashCode(it)] = it
                }
                VisitResult.Descend
            }
            "tkhd" -> {
                val t = currentTrack ?: return VisitResult.Skip
                readTkhd(source, payloadStart, payloadEnd, t)
                // Re-key by real trackId.
                val placeholderKeys = tracksById.entries.firstOrNull { it.value === t }?.key
                if (placeholderKeys != null && placeholderKeys != t.trackId) {
                    tracksById.remove(placeholderKeys)
                    tracksById[t.trackId] = t
                }
                VisitResult.Skip
            }
            "mdhd" -> {
                currentTrack?.let { readMdhd(source, payloadStart, it) }
                VisitResult.Skip
            }
            "hdlr" -> {
                currentTrack?.let { it.handler = readHdlrType(source, payloadStart) }
                VisitResult.Skip
            }
            "stco" -> {
                currentTrack?.let { it.chunkOffsets = readStco(source, payloadStart, is64 = false) }
                VisitResult.Skip
            }
            "co64" -> {
                currentTrack?.let { it.chunkOffsets = readStco(source, payloadStart, is64 = true) }
                VisitResult.Skip
            }
            "stsz" -> {
                currentTrack?.let { it.sampleSizes = readStsz(source, payloadStart) }
                VisitResult.Skip
            }
            "stsc" -> {
                currentTrack?.let { it.sampleToChunk = readStsc(source, payloadStart) }
                VisitResult.Skip
            }
            "stts" -> {
                currentTrack?.let { it.timeToSample = readStts(source, payloadStart) }
                VisitResult.Skip
            }
            "chap" -> {
                // tref/chap: list of 4-byte big-endian track IDs.
                chapTrackIds += readTrefChap(source, payloadStart, payloadEnd)
                VisitResult.Skip
            }
            "chpl" -> {
                neroChapters += readChpl(source, payloadStart, payloadEnd)
                VisitResult.Skip
            }
            else -> VisitResult.Descend
        }
    }

    private fun readTkhd(s: SeekableSource, start: Long, end: Long, t: Mp4Track) {
        s.seek(start)
        val version = s.readByte()
        s.readByte(); s.readByte(); s.readByte() // flags
        if (version == 1) {
            // 8 creation + 8 modification
            s.readUInt64BE(); s.readUInt64BE()
            t.trackId = (s.readUInt32BE() and 0xFFFFFFFFL).toInt()
        } else {
            s.readUInt32BE(); s.readUInt32BE()
            t.trackId = (s.readUInt32BE() and 0xFFFFFFFFL).toInt()
        }
    }

    private fun readMdhd(s: SeekableSource, start: Long, t: Mp4Track) {
        s.seek(start)
        val version = s.readByte()
        s.readByte(); s.readByte(); s.readByte() // flags
        if (version == 1) {
            s.readUInt64BE(); s.readUInt64BE()
            t.timescale = s.readUInt32BE()
        } else {
            s.readUInt32BE(); s.readUInt32BE()
            t.timescale = s.readUInt32BE()
        }
    }

    private fun readHdlrType(s: SeekableSource, start: Long): String {
        s.seek(start)
        s.readUInt32BE() // version+flags
        s.readUInt32BE() // pre_defined
        val buf = ByteArray(4)
        s.readFully(buf, 0, 4)
        return String(buf, Charsets.ISO_8859_1)
    }

    private fun readStco(s: SeekableSource, start: Long, is64: Boolean): LongArray {
        s.seek(start)
        s.readUInt32BE() // version+flags
        val count = s.readUInt32BE().toInt()
        val out = LongArray(count)
        for (i in 0 until count) out[i] = if (is64) s.readUInt64BE() else s.readUInt32BE()
        return out
    }

    private fun readStsz(s: SeekableSource, start: Long): IntArray {
        s.seek(start)
        s.readUInt32BE() // version+flags
        val sampleSize = s.readUInt32BE().toInt()
        val count = s.readUInt32BE().toInt()
        return if (sampleSize != 0) {
            IntArray(count) { sampleSize }
        } else {
            IntArray(count) { s.readUInt32BE().toInt() }
        }
    }

    private fun readStsc(s: SeekableSource, start: Long): List<StscEntry> {
        s.seek(start)
        s.readUInt32BE() // version+flags
        val count = s.readUInt32BE().toInt()
        val out = ArrayList<StscEntry>(count)
        for (i in 0 until count) {
            out += StscEntry(
                firstChunk = s.readUInt32BE().toInt(),
                samplesPerChunk = s.readUInt32BE().toInt(),
                sampleDescIndex = s.readUInt32BE().toInt(),
            )
        }
        return out
    }

    private fun readStts(s: SeekableSource, start: Long): List<SttsEntry> {
        s.seek(start)
        s.readUInt32BE() // version+flags
        val count = s.readUInt32BE().toInt()
        val out = ArrayList<SttsEntry>(count)
        for (i in 0 until count) {
            out += SttsEntry(
                sampleCount = s.readUInt32BE().toInt(),
                sampleDelta = s.readUInt32BE(),
            )
        }
        return out
    }

    private fun readTrefChap(s: SeekableSource, start: Long, end: Long): List<Int> {
        s.seek(start)
        val n = ((end - start) / 4).toInt()
        return List(n) { (s.readUInt32BE() and 0xFFFFFFFFL).toInt() }
    }

    private fun readChpl(s: SeekableSource, start: Long, end: Long): List<ChapterMark> {
        s.seek(start)
        // [1 version][3 flags][... count ...] count is either 1-byte (older) or 4-byte
        // depending on producer. The widely-deployed Nero/mp4chaps format puts a 4-byte
        // count after a 1-byte reserved field; some older taggers emit a 1-byte count.
        // Disambiguate by trying the 4-byte path first, sanity-check, fall back.
        val version = s.readByte()
        s.readByte(); s.readByte(); s.readByte() // flags
        // Standard layout used by mp4v2/Nero: [1-byte reserved][4-byte count].
        // Some 1.0 producers omit the reserved byte. Detect by reading position.
        val payloadRemaining = end - s.position()
        return parseChplBody(s, end, version, payloadRemaining)
    }

    private fun parseChplBody(s: SeekableSource, end: Long, version: Int, remaining: Long): List<ChapterMark> {
        // Speculate "Nero 4-byte count" layout: [1 reserved][4 count][entries...]
        val mark = s.position()
        if (version == 0) {
            // Old chpl: [1 count][entries...]
            return tryParseChpl(s, end, has4ByteCount = false) ?: run {
                s.seek(mark)
                tryParseChpl(s, end, has4ByteCount = true) ?: emptyList()
            }
        }
        return tryParseChpl(s, end, has4ByteCount = true) ?: run {
            s.seek(mark)
            tryParseChpl(s, end, has4ByteCount = false) ?: emptyList()
        }
    }

    private fun tryParseChpl(s: SeekableSource, end: Long, has4ByteCount: Boolean): List<ChapterMark>? {
        val startPos = s.position()
        return try {
            val count = if (has4ByteCount) {
                s.readByte() // reserved
                s.readUInt32BE().toInt()
            } else {
                s.readByte()
            }
            if (count < 0 || count > 4096) {
                s.seek(startPos); return null
            }
            val out = ArrayList<ChapterMark>(count)
            for (i in 0 until count) {
                if (s.position() + 9 > end) { s.seek(startPos); return null }
                val ts100ns = s.readUInt64BE()
                val nameLen = s.readByte()
                if (s.position() + nameLen > end) { s.seek(startPos); return null }
                val nameBuf = ByteArray(nameLen)
                s.readFully(nameBuf, 0, nameLen)
                out += ChapterMark(
                    positionMs = ts100ns / 10_000L,
                    title = String(nameBuf, Charsets.UTF_8),
                )
            }
            out
        } catch (_: Throwable) {
            s.seek(startPos)
            null
        }
    }
}
