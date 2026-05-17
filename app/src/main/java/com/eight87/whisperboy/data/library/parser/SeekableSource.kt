package com.eight87.whisperboy.data.library.parser

import java.io.Closeable

/**
 * Random-access byte reader the chapter parsers consume.
 *
 * Two implementations:
 *
 * - [SafSeekableDataSource] (production) — wraps a SAF `Uri` via
 *   `ContentResolver.openAssetFileDescriptor` and a `RandomAccessFile` over the descriptor.
 * - [ByteArraySeekableSource] (tests) — pure in-memory fixture.
 *
 * The interface lets parsers be tested without `Context` / `ContentResolver`. SAF streams
 * are forward-only by default; the production impl pays the descriptor cost so the parsers
 * can `seek` freely (MP4 atom walks + EBML walks both jump around the file).
 */
interface SeekableSource : Closeable {

    /** Total length in bytes, or `-1` if unknown. */
    val length: Long

    /** Current read position. */
    fun position(): Long

    /** Seek to absolute byte offset. */
    fun seek(offset: Long)

    /**
     * Read up to [length] bytes into [buffer] starting at [offset]. Returns the number of
     * bytes actually read, or `-1` at end-of-stream. Like `InputStream.read`, may return
     * fewer bytes than requested.
     */
    fun read(buffer: ByteArray, offset: Int, length: Int): Int

    /** Convenience: read exactly [length] bytes or throw. */
    fun readFully(buffer: ByteArray, offset: Int, length: Int) {
        var read = 0
        while (read < length) {
            val n = read(buffer, offset + read, length - read)
            if (n < 0) throw java.io.EOFException("EOF at ${position()} after $read/$length bytes")
            read += n
        }
    }

    fun readByte(): Int {
        val b = ByteArray(1)
        readFully(b, 0, 1)
        return b[0].toInt() and 0xFF
    }

    fun readUInt32BE(): Long {
        val b = ByteArray(4)
        readFully(b, 0, 4)
        return ((b[0].toLong() and 0xFF) shl 24) or
            ((b[1].toLong() and 0xFF) shl 16) or
            ((b[2].toLong() and 0xFF) shl 8) or
            (b[3].toLong() and 0xFF)
    }

    fun readUInt64BE(): Long {
        val b = ByteArray(8)
        readFully(b, 0, 8)
        var v = 0L
        for (i in 0 until 8) {
            v = (v shl 8) or (b[i].toLong() and 0xFF)
        }
        return v
    }
}
