package com.eight87.whisperboy.data.library

/**
 * Output of a single scan pass. Phase D.4's `LibraryRepository.applyScan(snapshot)` consumes
 * this and writes to Room in one transaction, marking books that are no longer in any
 * snapshot as `active = false` (soft-delete preserves bookmarks if the user re-adds the
 * folder).
 *
 * The scan-side types ([ScannedBook] / [ScannedChapter]) are deliberately separate from the
 * Room entities ([BookEntity] / [ChapterEntity]) — see R.F.4 in `docs/plans/refactor-solid.md`.
 * Scan-only fields like raw cover bytes can live here without polluting the cache schema.
 */
data class ScanSnapshot(
    val books: List<ScannedBook>,
)

/**
 * A book the scanner found in one of the user's [LibraryRoot]s.
 *
 * Phase D.2 fills [bookId], [treeUriString], [relativePath], [title], [author], and the
 * structural [chapters] list (chapter ids, indices, file URIs). [durationMs] and per-chapter
 * [ScannedChapter.durationMs] / [ScannedChapter.title] / cover stay at their defaults until
 * Phase D.3's `MediaAnalyzer` enriches them via `MediaMetadataRetriever`.
 */
data class ScannedBook(
    val bookId: String,
    val treeUriString: String,
    val relativePath: String,
    val title: String,
    val author: String? = null,
    val chapters: List<ScannedChapter>,
    val durationMs: Long = 0L,
    val coverPath: String? = null,
    /**
     * Raw cover bytes pulled from the first chapter's embedded picture by Phase D.3's
     * enrichment. Phase D.4's `applyScan` writes these to disk via `CoverStore` and stores
     * the resulting absolute path on the [BookEntity] row. `null` = no embedded cover found.
     */
    val embeddedCoverBytes: ByteArray? = null,
) {
    /** Custom equals/hashCode because [ByteArray] uses identity comparison by default. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScannedBook) return false
        if (bookId != other.bookId) return false
        if (treeUriString != other.treeUriString) return false
        if (relativePath != other.relativePath) return false
        if (title != other.title) return false
        if (author != other.author) return false
        if (chapters != other.chapters) return false
        if (durationMs != other.durationMs) return false
        if (coverPath != other.coverPath) return false
        if (embeddedCoverBytes == null) {
            if (other.embeddedCoverBytes != null) return false
        } else {
            if (other.embeddedCoverBytes == null) return false
            if (!embeddedCoverBytes.contentEquals(other.embeddedCoverBytes)) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = bookId.hashCode()
        result = 31 * result + treeUriString.hashCode()
        result = 31 * result + relativePath.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (author?.hashCode() ?: 0)
        result = 31 * result + chapters.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + (coverPath?.hashCode() ?: 0)
        result = 31 * result + (embeddedCoverBytes?.contentHashCode() ?: 0)
        return result
    }
}

data class ScannedChapter(
    val chapterId: String,
    val chapterIndex: Int,
    val title: String? = null,
    val durationMs: Long = 0L,
    val fileUri: String? = null,
    val positionInBookMs: Long = 0L,
)
