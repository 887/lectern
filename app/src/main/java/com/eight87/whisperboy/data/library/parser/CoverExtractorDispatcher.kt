package com.eight87.whisperboy.data.library.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Public entry point for the dedicated per-container cover extractors. Phase A.3.
 *
 * Routes by MIME hint when available, otherwise sniffs container magic bytes (mirrors
 * [ChapterParser]'s detection strategy). Bad files don't propagate exceptions — they return
 * `null` and log under `whisperboy.cover`.
 *
 * Called from [com.eight87.whisperboy.data.library.LibraryScannerEnrichment] only when the
 * Media3 [com.eight87.whisperboy.data.library.MediaAnalyzer] pass came back with no embedded
 * cover; cover-art Phase A doctrine requires us to try the first 5 chapters before giving up.
 */
class CoverExtractorDispatcher(private val context: Context) {

    suspend fun extract(uri: Uri, mimeType: String? = null, fileName: String? = null): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                SafSeekableDataSource.open(context, uri).use { source ->
                    val kind = detectContainer(source, mimeType, fileName)
                    val extractor: CoverExtractor? = when (kind) {
                        Container.Mp4 -> Mp4CoverExtractor(source)
                        Container.Matroska -> MatroskaCoverExtractor(source)
                        Container.Mp3 -> Mp3CoverExtractor(source)
                        Container.Unknown -> null
                    }
                    extractor?.extract()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "extract failed for $uri: ${t.javaClass.simpleName} ${t.message}")
                null
            }
        }

    private fun detectContainer(
        source: SeekableSource,
        mimeType: String?,
        fileName: String?,
    ): Container {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase()
        if (mimeType != null) {
            if (mimeType.contains("mp4") || mimeType.contains("m4b") || mimeType.contains("m4a")) return Container.Mp4
            if (mimeType.contains("matroska") || mimeType.contains("webm") || mimeType.contains("mkv")) return Container.Matroska
            if (mimeType.contains("mpeg") || mimeType.contains("mp3")) return Container.Mp3
        }
        if (ext != null) {
            when (ext) {
                "m4b", "m4a", "mp4", "aac" -> return Container.Mp4
                "mka", "mkv", "webm" -> return Container.Matroska
                "mp3" -> return Container.Mp3
            }
        }
        return sniffMagic(source)
    }

    private fun sniffMagic(source: SeekableSource): Container {
        if (source.length < 8) return Container.Unknown
        val head = ByteArray(8)
        source.seek(0)
        val n = source.read(head, 0, 8)
        if (n < 8) return Container.Unknown
        // MP4 'ftyp' at offset 4.
        if (head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() &&
            head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte()) return Container.Mp4
        // EBML header at offset 0: 0x1A 0x45 0xDF 0xA3.
        if (head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() &&
            head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte()) return Container.Matroska
        // ID3v2 header — only MP3-of-interest carries APIC; raw MPEG without a tag has no
        // cover so we don't need to recognise frame-sync bytes.
        if (head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() &&
            head[2] == '3'.code.toByte()) return Container.Mp3
        return Container.Unknown
    }

    private enum class Container { Mp4, Matroska, Mp3, Unknown }

    private companion object {
        const val TAG = "whisperboy.cover"
    }
}
