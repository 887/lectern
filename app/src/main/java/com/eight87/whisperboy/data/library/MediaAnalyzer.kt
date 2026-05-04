package com.eight87.whisperboy.data.library

import android.net.Uri

/**
 * Narrow data interface (Phase R.A pattern) for per-audio-file metadata extraction.
 *
 * Phase D.3 ships [Media3MediaAnalyzer] as the only implementation; tests can substitute fakes.
 * Phase D.2's structural [SafLibraryScanner] hands URIs to this interface; the per-file
 * results then flow through [LibraryScannerEnrichment] back into a fully-populated
 * [ScanSnapshot] ready for Phase D.4's `applyScan`.
 *
 * Returns `null` for unreadable / non-audio / corrupted files — callers should treat that as
 * "skip enrichment, leave defaults" rather than "crash the scan".
 */
interface MediaAnalyzer {

    suspend fun extract(fileUri: Uri): FileMetadata?
}

/**
 * What [MediaAnalyzer] pulls out of a single audio file. All fields nullable / zero-defaulted
 * so a partial extraction (e.g. duration available, no title tag) is representable without
 * forcing the caller to handle exceptions.
 */
data class FileMetadata(
    val durationMs: Long = 0L,
    val title: String? = null,
    val author: String? = null,
    val embeddedCoverBytes: ByteArray? = null,
) {
    /** Custom equals/hashCode because [ByteArray] uses identity comparison by default. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileMetadata) return false
        if (durationMs != other.durationMs) return false
        if (title != other.title) return false
        if (author != other.author) return false
        if (embeddedCoverBytes == null) {
            if (other.embeddedCoverBytes != null) return false
        } else {
            if (other.embeddedCoverBytes == null) return false
            if (!embeddedCoverBytes.contentEquals(other.embeddedCoverBytes)) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = durationMs.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (author?.hashCode() ?: 0)
        result = 31 * result + (embeddedCoverBytes?.contentHashCode() ?: 0)
        return result
    }
}
