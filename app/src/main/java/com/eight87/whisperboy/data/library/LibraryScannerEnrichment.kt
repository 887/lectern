package com.eight87.whisperboy.data.library

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase D.3 glue between D.2's structural [ScanSnapshot] and the per-file [MediaAnalyzer].
 *
 * Walks each [ScannedBook]'s chapters, calls [MediaAnalyzer.extract] for every chapter that
 * has a `fileUri`, fills in `durationMs` / `title`, computes the cumulative `positionInBookMs`
 * across the chapter list, and rolls book-level fields up:
 *
 * - Book `durationMs` = sum of enriched chapter durations.
 * - Book `author` = first non-null per-chapter author (most audiobook folders are
 *   single-author so this picks the right value cheaply).
 * - Book `title` is preserved from the structural pass — Phase D.2's folder-name title is
 *   usually correct for SingleFolder / Root / Author modes; the per-file `title` tag is more
 *   often the chapter name, not the book name.
 * - Book `coverPath` is left null here — the actual cover-bytes-to-disk write happens in
 *   Phase D.4's `applyScan` so the file lifecycle stays bound to the row write transaction.
 *   This phase only tells D.4 *whether* a cover was found and *where the bytes came from*
 *   (embedded vs folder file).
 *
 * Suspend, runs on `Dispatchers.IO`. Bad files don't crash the pass — `MediaAnalyzer.extract`
 * returning `null` leaves that chapter's structural defaults intact.
 */
class LibraryScannerEnrichment(
    private val mediaAnalyzer: MediaAnalyzer,
    private val folderCoverFinder: FolderCoverFinder,
) {

    suspend fun enrich(snapshot: ScanSnapshot): ScanSnapshot = withContext(Dispatchers.IO) {
        ScanSnapshot(snapshot.books.map { enrichBook(it) })
    }

    private suspend fun enrichBook(book: ScannedBook): ScannedBook {
        val perChapterMetadata: List<FileMetadata?> = book.chapters.map { chapter ->
            chapter.fileUri?.let { uriString ->
                runCatching { Uri.parse(uriString) }.getOrNull()?.let { mediaAnalyzer.extract(it) }
            }
        }

        // Compute cumulative positionInBookMs as we enrich each chapter's duration.
        var cumulative = 0L
        val enrichedChapters = book.chapters.zip(perChapterMetadata) { chapter, metadata ->
            val durationMs = metadata?.durationMs ?: chapter.durationMs
            val title = chapter.title ?: metadata?.title
            val withFields = chapter.copy(
                durationMs = durationMs,
                title = title,
                positionInBookMs = cumulative,
            )
            cumulative += durationMs
            withFields
        }

        // Roll up book-level fields.
        val rolledAuthor = book.author ?: perChapterMetadata.firstNotNullOfOrNull { it?.author }
        val rolledDuration = enrichedChapters.sumOf { it.durationMs }
        // Cover-art Phase A local-first preference order: folder-level bytes from
        // SafLibraryScanner / FolderCoverFinder win; only fall back to embedded extraction
        // when the scanner didn't already find a sidecar image. Mirrors Voice's order
        // (folder file > embedded). See `docs/plans/cover-art.md` doctrine #1.
        val resolvedCover = book.embeddedCoverBytes
            ?: perChapterMetadata.firstNotNullOfOrNull { it?.embeddedCoverBytes }

        return book.copy(
            chapters = enrichedChapters,
            author = rolledAuthor,
            durationMs = rolledDuration,
            embeddedCoverBytes = resolvedCover,
        )
    }
}
