package com.eight87.whisperboy.data.library.parser

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Random-access reader over a SAF [Uri]. Phase I.1.
 *
 * SAF `InputStream`s are forward-only. To `seek` freely (the MP4 atom walker + EBML walker
 * both jump around the file), we open the document as an `AssetFileDescriptor`, take a
 * [FileInputStream] over the underlying `FileDescriptor`, and use its [FileChannel] for
 * positional reads. This is the canonical Android pattern for random access on a
 * content-resolver-supplied file — `RandomAccessFile` cannot wrap a `FileDescriptor`
 * obtained from a content resolver because its `FileDescriptor` constructor is package-private.
 *
 * Closes the channel, the stream, and the descriptor on [close].
 */
internal class SafSeekableDataSource private constructor(
    private val stream: FileInputStream,
    private val channel: FileChannel,
    private val afd: android.content.res.AssetFileDescriptor,
    override val length: Long,
) : SeekableSource {

    private var pos: Long = 0L

    override fun position(): Long = pos

    override fun seek(offset: Long) {
        pos = offset
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val buf = ByteBuffer.wrap(buffer, offset, length)
        val n = channel.read(buf, pos)
        if (n > 0) pos += n
        return n
    }

    override fun close() {
        runCatching { channel.close() }
        runCatching { stream.close() }
        runCatching { afd.close() }
    }

    companion object {
        fun open(context: Context, uri: Uri): SafSeekableDataSource {
            val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
                ?: throw java.io.IOException("Unable to open AssetFileDescriptor for $uri")
            val stream = FileInputStream(afd.fileDescriptor)
            val channel = stream.channel
            val len = runCatching { channel.size() }.getOrDefault(afd.length)
            return SafSeekableDataSource(stream, channel, afd, len)
        }
    }
}
