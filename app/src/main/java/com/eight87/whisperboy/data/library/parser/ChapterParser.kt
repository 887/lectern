package com.eight87.whisperboy.data.library.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Public entry point for embedded-chapter extraction. Phase I.7.
 *
 * Sniffs the container by magic bytes (with extension / MIME as a hint), routes to the right
 * parser, returns a list of [ChapterMark]s. Returns empty list when nothing parses — callers
 * should fall back to a synthesised single-chapter row.
 *
 * Bad files don't propagate exceptions — they return an empty list and log under
 * `whisperboy.parser`.
 */
class ChapterParser(private val context: Context) {

    suspend fun parse(uri: Uri, mimeType: String? = null, fileName: String? = null): List<ChapterMark> =
        withContext(Dispatchers.IO) {
            try {
                SafSeekableDataSource.open(context, uri).use { source ->
                    val kind = detectContainer(source, mimeType, fileName)
                    when (kind) {
                        Container.Mp4 -> Mp4ChapterParser(source).parse()
                        Container.Matroska -> MatroskaChapterParser(source).parse()
                        Container.Ogg -> VorbisChapterParser(source).parse()
                        Container.Unknown -> emptyList()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "parse failed for $uri: ${t.javaClass.simpleName} ${t.message}")
                emptyList()
            }
        }

    private fun detectContainer(
        source: SeekableSource,
        mimeType: String?,
        fileName: String?,
    ): Container {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase()
        // MIME hint comes first because providers often supply it correctly.
        if (mimeType != null) {
            if (mimeType.contains("mp4") || mimeType.contains("m4b") || mimeType.contains("m4a")) return Container.Mp4
            if (mimeType.contains("matroska") || mimeType.contains("webm") || mimeType.contains("mkv")) return Container.Matroska
            if (mimeType.contains("ogg") || mimeType.contains("opus") || mimeType.contains("vorbis")) return Container.Ogg
        }
        if (ext != null) {
            when (ext) {
                "m4b", "m4a", "mp4", "aac" -> return Container.Mp4
                "mka", "mkv", "webm" -> return Container.Matroska
                "ogg", "oga", "opus" -> return Container.Ogg
            }
        }
        // Magic-byte sniff fallback.
        return sniffMagic(source)
    }

    private fun sniffMagic(source: SeekableSource): Container {
        if (source.length < 8) return Container.Unknown
        val head = ByteArray(8)
        source.seek(0)
        val n = source.read(head, 0, 8)
        if (n < 8) return Container.Unknown
        // ftyp/moov at offset 4: 'f','t','y','p' or 'm','o','o','v' or 'm','d','a','t' or 'f','r','e','e'
        if (head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() &&
            head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte()) return Container.Mp4
        // EBML header at offset 0: 0x1A 0x45 0xDF 0xA3
        if (head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() &&
            head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte()) return Container.Matroska
        // 'O','g','g','S'
        if (head[0] == 'O'.code.toByte() && head[1] == 'g'.code.toByte() &&
            head[2] == 'g'.code.toByte() && head[3] == 'S'.code.toByte()) return Container.Ogg
        return Container.Unknown
    }

    private enum class Container { Mp4, Matroska, Ogg, Unknown }

    private companion object {
        const val TAG = "whisperboy.parser"
    }
}
