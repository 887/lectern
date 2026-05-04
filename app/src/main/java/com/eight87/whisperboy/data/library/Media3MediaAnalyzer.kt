package com.eight87.whisperboy.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android-side implementation of [MediaAnalyzer] using [MediaMetadataRetriever].
 *
 * Each [extract] call opens the file via SAF, reads the standard metadata keys, then releases
 * the retriever. **Bad files do not crash the scan** — exceptions are caught and turned into
 * a `null` return so the caller (Phase D.3's [LibraryScannerEnrichment]) can fall back to the
 * structural-only chapter row.
 *
 * Voice analog: `voice.core.scanner.MediaAnalyzer`. The keys are the same set Voice uses.
 */
class Media3MediaAnalyzer(private val context: Context) : MediaAnalyzer {

    override suspend fun extract(fileUri: Uri): FileMetadata? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, fileUri)
            FileMetadata(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: 0L,
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() },
                author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() }
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                        ?.takeIf { it.isNotBlank() },
                embeddedCoverBytes = retriever.embeddedPicture,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "extract failed for $fileUri: ${t.javaClass.simpleName} ${t.message}")
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
                // Releasing a half-initialized retriever can throw on some OEMs; swallow.
            }
        }
    }

    private companion object {
        const val TAG = "whisperboy.media"
    }
}
