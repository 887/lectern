package com.eight87.whisperboy.data.library.parser

/**
 * Reads the embedded cover from an MP3 file's ID3v2 `APIC` frame. Phase A.3.
 *
 * Minimal ID3v2 walker — only enough to find the picture frame and return its bytes. We do
 * not implement the full tag (genre lookups, text-frame encoding round-trips, footer frames,
 * unsynchronisation, etc.). Supported tag versions: ID3v2.2 (`PIC` frame), ID3v2.3, and
 * ID3v2.4 (`APIC`). v2.4 frame sizes are syncsafe; v2.3 frame sizes are plain BE32.
 *
 * APIC frame body layout (v2.3 / v2.4):
 * ```
 * [1 text-encoding][mime-type NUL-terminated ASCII][1 picture-type]
 * [description NUL-terminated, encoding-aware][image bytes]
 * ```
 *
 * PIC frame body layout (v2.2 — 3-letter frame ID, 3-byte plain BE size):
 * ```
 * [1 text-encoding][3 image-format ASCII][1 picture-type]
 * [description NUL-terminated][image bytes]
 * ```
 *
 * Returns the first picture found. Most files only have one, the canonical front-cover entry.
 */
internal class Mp3CoverExtractor(private val source: SeekableSource) : CoverExtractor {

    override fun extract(): ByteArray? {
        if (source.length < 10) return null
        source.seek(0)
        val header = ByteArray(10)
        source.readFully(header, 0, 10)
        if (header[0] != 'I'.code.toByte() ||
            header[1] != 'D'.code.toByte() ||
            header[2] != '3'.code.toByte()
        ) return null
        val major = header[3].toInt() and 0xFF
        if (major !in 2..4) return null
        val flags = header[5].toInt() and 0xFF
        val tagSize = readSyncsafe(header, 6)
        if (tagSize <= 0 || 10L + tagSize > source.length) return null
        val tagEnd = 10L + tagSize
        var cursor = 10L

        // Skip extended header if present (v2.3 + v2.4).
        if (major >= 3 && (flags and 0x40) != 0) {
            source.seek(cursor)
            val extSize: Long = if (major == 4) {
                // v2.4 extended header size is syncsafe and INCLUDES its own 4 size bytes.
                readSyncsafeBE(source).toLong()
            } else {
                // v2.3 extended header size is non-syncsafe and EXCLUDES its own 4 size bytes.
                source.readUInt32BE() + 4L
            }
            cursor += extSize.coerceAtLeast(0L)
        }

        val frameIdLen = if (major == 2) 3 else 4
        val frameSizeLen = if (major == 2) 3 else 4
        val frameHeaderLen = frameIdLen + frameSizeLen + if (major == 2) 0 else 2

        while (cursor + frameHeaderLen <= tagEnd) {
            source.seek(cursor)
            val idBuf = ByteArray(frameIdLen)
            source.readFully(idBuf, 0, frameIdLen)
            if (idBuf[0] == 0.toByte()) break // padding region — done.
            val frameId = String(idBuf, Charsets.ISO_8859_1)
            val frameSize: Long = when (major) {
                4 -> readSyncsafeBE(source).toLong()
                3 -> source.readUInt32BE()
                else -> readUInt24BE(source).toLong()
            }
            if (major >= 3) {
                source.readByte(); source.readByte() // frame flags
            }
            val frameBodyStart = source.position()
            val frameBodyEnd = (frameBodyStart + frameSize).coerceAtMost(tagEnd)
            if (frameSize <= 0 || frameBodyEnd > tagEnd) break

            val isPicture = (major == 2 && frameId == "PIC") ||
                (major >= 3 && frameId == "APIC")
            if (isPicture) {
                val bytes = decodePictureFrame(frameBodyStart, frameBodyEnd, isV22 = major == 2)
                if (bytes != null) return bytes
            }
            cursor = frameBodyEnd
        }
        return null
    }

    private fun decodePictureFrame(start: Long, end: Long, isV22: Boolean): ByteArray? {
        if (end - start < 4) return null
        source.seek(start)
        val encoding = source.readByte()
        // v2.2: 3-byte image-format string; v2.3+: NUL-terminated MIME type ASCII.
        if (isV22) {
            val fmt = ByteArray(3)
            source.readFully(fmt, 0, 3)
            // image-format ignored — Coil sniffs the bytes.
        } else {
            // MIME type — read until NUL.
            if (!skipNulTerminatedAscii(end)) return null
        }
        if (source.position() >= end) return null
        source.readByte() // picture-type
        // Description — encoding-aware NUL terminator.
        if (!skipNulTerminatedByEncoding(encoding, end)) return null
        val imageLen = (end - source.position()).toInt()
        if (imageLen <= 0) return null
        val out = ByteArray(imageLen)
        source.readFully(out, 0, imageLen)
        return out
    }

    private fun skipNulTerminatedAscii(end: Long): Boolean {
        while (source.position() < end) {
            val b = source.readByte()
            if (b == 0) return true
        }
        return false
    }

    /** Skip the description string. UTF-16 (encoding 1/2) uses 2-byte NUL terminator. */
    private fun skipNulTerminatedByEncoding(encoding: Int, end: Long): Boolean {
        return when (encoding) {
            1, 2 -> {
                // UTF-16 with BOM (1) or UTF-16BE (2) — 2-byte NUL.
                while (source.position() + 1 < end) {
                    val hi = source.readByte()
                    val lo = source.readByte()
                    if (hi == 0 && lo == 0) return true
                }
                false
            }
            else -> skipNulTerminatedAscii(end)
        }
    }

    private fun readSyncsafe(buf: ByteArray, offset: Int): Long {
        val b0 = buf[offset].toLong() and 0x7F
        val b1 = buf[offset + 1].toLong() and 0x7F
        val b2 = buf[offset + 2].toLong() and 0x7F
        val b3 = buf[offset + 3].toLong() and 0x7F
        return (b0 shl 21) or (b1 shl 14) or (b2 shl 7) or b3
    }

    private fun readSyncsafeBE(s: SeekableSource): Int {
        val b0 = s.readByte() and 0x7F
        val b1 = s.readByte() and 0x7F
        val b2 = s.readByte() and 0x7F
        val b3 = s.readByte() and 0x7F
        return (b0 shl 21) or (b1 shl 14) or (b2 shl 7) or b3
    }

    private fun readUInt24BE(s: SeekableSource): Int {
        val b0 = s.readByte()
        val b1 = s.readByte()
        val b2 = s.readByte()
        return (b0 shl 16) or (b1 shl 8) or b2
    }
}
