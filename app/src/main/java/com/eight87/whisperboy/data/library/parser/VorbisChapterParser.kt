package com.eight87.whisperboy.data.library.parser

/**
 * Vorbis-comment chapter extractor. Phase I.6.
 *
 * Vorbis comments live in the Vorbis "comment" packet (Vorbis I, packet type 0x03) inside an
 * Ogg stream, or in the Opus "OpusTags" packet. Both share the same comment-block layout:
 *
 * ```
 * [4-byte LE length][vendor string]
 * [4-byte LE comment count]
 * for each comment:
 *   [4-byte LE length][UTF-8 "KEY=VALUE"]
 * ```
 *
 * The chapter convention used by tools like chapter-and-verse / mkvtoolnix:
 *
 * - `CHAPTER001=hh:mm:ss.sss` — chapter start time
 * - `CHAPTER001NAME=...`      — chapter display title
 *
 * Numbers are 1-based, zero-padded to (at least) 3 digits in practice but the parser
 * tolerates 1+ digits.
 */
internal class VorbisChapterParser(private val source: SeekableSource) {

    fun parse(): List<ChapterMark> {
        val comments = extractCommentBlock() ?: return emptyList()
        return parseCommentsToMarks(comments)
    }

    /**
     * Reassemble the second Ogg packet (Vorbis comment / OpusTags), then strip the type
     * marker so what remains is the raw comment block.
     */
    private fun extractCommentBlock(): List<String>? {
        val secondPacket = readOggPacket(skip = 1) ?: return null
        // Strip codec-specific header: Vorbis is [0x03, 'v','o','r','b','i','s']; Opus is "OpusTags".
        val payload = when {
            secondPacket.size >= 7 &&
                secondPacket[0] == 0x03.toByte() &&
                secondPacket.copyOfRange(1, 7).contentEquals("vorbis".toByteArray()) ->
                secondPacket.copyOfRange(7, secondPacket.size)
            secondPacket.size >= 8 &&
                secondPacket.copyOfRange(0, 8).contentEquals("OpusTags".toByteArray()) ->
                secondPacket.copyOfRange(8, secondPacket.size)
            else -> secondPacket
        }
        return decodeCommentBlock(payload)
    }

    private fun decodeCommentBlock(block: ByteArray): List<String>? {
        var i = 0
        fun le32(): Int {
            if (i + 4 > block.size) return -1
            val v = (block[i].toInt() and 0xFF) or
                ((block[i + 1].toInt() and 0xFF) shl 8) or
                ((block[i + 2].toInt() and 0xFF) shl 16) or
                ((block[i + 3].toInt() and 0xFF) shl 24)
            i += 4
            return v
        }
        val vendorLen = le32(); if (vendorLen < 0 || i + vendorLen > block.size) return null
        i += vendorLen
        val count = le32(); if (count < 0 || count > 65536) return null
        val out = ArrayList<String>(count)
        for (k in 0 until count) {
            val len = le32(); if (len < 0 || i + len > block.size) return null
            out += String(block, i, len, Charsets.UTF_8)
            i += len
        }
        return out
    }

    /**
     * Walk Ogg pages and assemble the [skip]-th-and-then-next packet. A packet may span
     * multiple pages (continuation when first lacing value of next page is part of an unfinished
     * packet) but in practice the Vorbis comment header is small and fits in one page.
     */
    private fun readOggPacket(skip: Int): ByteArray? {
        source.seek(0)
        var packetsSeen = 0
        val current = java.io.ByteArrayOutputStream()
        var pendingContinuation = false
        val pageLen = source.length
        while (source.position() < pageLen) {
            val pageStart = source.position()
            val sig = ByteArray(4)
            val n = source.read(sig, 0, 4)
            if (n < 4) return null
            if (!sig.contentEquals(OGG_S)) return null
            // version(1) + header type(1) + granule(8) + serial(4) + seq(4) + crc(4)
            source.seek(pageStart + 5)
            val headerType = source.readByte()
            source.seek(pageStart + 26)
            val segCount = source.readByte()
            val segTable = ByteArray(segCount)
            source.readFully(segTable, 0, segCount)
            val dataStart = source.position()
            // Iterate segments, splitting at non-255-byte boundaries (end of packet).
            var dataCursor = dataStart
            var packetBytes = 0
            val isContinued = (headerType and 0x01) != 0
            if (!isContinued && current.size() > 0 && pendingContinuation) {
                // Page didn't continue but we had open packet — flush.
                if (packetsSeen == skip) return current.toByteArray()
                packetsSeen++
                current.reset()
            }
            for (segIdx in 0 until segCount) {
                val segLen = segTable[segIdx].toInt() and 0xFF
                packetBytes += segLen
                if (segLen < 255) {
                    // Packet ends here. Append accumulated bytes to current.
                    val take = ByteArray(packetBytes)
                    source.seek(dataCursor)
                    source.readFully(take, 0, packetBytes)
                    current.write(take)
                    dataCursor += packetBytes
                    packetBytes = 0
                    if (packetsSeen == skip) return current.toByteArray()
                    packetsSeen++
                    current.reset()
                    pendingContinuation = false
                }
            }
            if (packetBytes > 0) {
                // Packet continues into next page.
                val take = ByteArray(packetBytes)
                source.seek(dataCursor)
                source.readFully(take, 0, packetBytes)
                current.write(take)
                dataCursor += packetBytes
                pendingContinuation = true
            }
            source.seek(dataCursor)
        }
        return null
    }

    private fun parseCommentsToMarks(comments: List<String>): List<ChapterMark> {
        // index → (timeMs?, title?)
        data class Acc(var timeMs: Long? = null, var title: String? = null)
        val byIndex = sortedMapOf<Int, Acc>()
        val timeRegex = Regex("""(?i)^CHAPTER(\d+)=(.*)$""")
        val nameRegex = Regex("""(?i)^CHAPTER(\d+)NAME=(.*)$""")
        for (c in comments) {
            nameRegex.matchEntire(c)?.let { m ->
                val idx = m.groupValues[1].toInt()
                byIndex.getOrPut(idx) { Acc() }.title = m.groupValues[2]
                return@let
            } ?: timeRegex.matchEntire(c)?.let { m ->
                val idx = m.groupValues[1].toInt()
                val ms = parseTimestamp(m.groupValues[2]) ?: return@let
                byIndex.getOrPut(idx) { Acc() }.timeMs = ms
            }
        }
        return byIndex.values.mapNotNull { acc ->
            acc.timeMs?.let { ChapterMark(positionMs = it, title = acc.title ?: "") }
        }
    }

    private fun parseTimestamp(s: String): Long? {
        // "hh:mm:ss.sss" — be tolerant of "mm:ss.sss" and "ss.sss" too.
        val parts = s.trim().split(":")
        return try {
            when (parts.size) {
                3 -> ((parts[0].toLong() * 3600) + (parts[1].toLong() * 60)) * 1000L +
                    (parts[2].toDouble() * 1000.0).toLong()
                2 -> (parts[0].toLong() * 60) * 1000L + (parts[1].toDouble() * 1000.0).toLong()
                1 -> (parts[0].toDouble() * 1000.0).toLong()
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private val OGG_S = "OggS".toByteArray()
    }
}
