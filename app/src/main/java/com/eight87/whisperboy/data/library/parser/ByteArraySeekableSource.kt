package com.eight87.whisperboy.data.library.parser

/**
 * Test-only [SeekableSource] over an in-memory byte array. Lets parser unit tests run on the
 * JVM without `Context` / `ContentResolver` / SAF.
 */
internal class ByteArraySeekableSource(private val bytes: ByteArray) : SeekableSource {

    private var pos: Int = 0

    override val length: Long get() = bytes.size.toLong()

    override fun position(): Long = pos.toLong()

    override fun seek(offset: Long) {
        require(offset in 0..bytes.size) { "seek out of range: $offset (len=${bytes.size})" }
        pos = offset.toInt()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (pos >= bytes.size) return -1
        val n = minOf(length, bytes.size - pos)
        System.arraycopy(bytes, pos, buffer, offset, n)
        pos += n
        return n
    }

    override fun close() = Unit
}
