package com.eight87.whisperboy.data.library

import android.net.Uri
import com.eight87.whisperboy.data.library.parser.ChapterMark
import com.eight87.whisperboy.data.library.parser.ChapterParser
import com.eight87.whisperboy.data.library.parser.CoverExtractorDispatcher
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
    private val chapterParser: ChapterParser? = null,
    private val coverExtractorDispatcher: CoverExtractorDispatcher? = null,
) {

    suspend fun enrich(snapshot: ScanSnapshot): ScanSnapshot = withContext(Dispatchers.IO) {
        ScanSnapshot(snapshot.books.map { enrichBook(it) })
    }

    /**
     * Issue-3 progress-reporting overload. [reportProgress] is invoked after each book's
     * enrichment with the cumulative `(booksEnriched, chaptersEnriched, currentFolder)` —
     * the in-library scan progress banner consumes this via [AndroidLibraryRescanCoordinator]
     * to surface "Scanning library — N books, M chapters" while the pipeline runs.
     *
     * Errors raised by [reportProgress] do not abort enrichment; the callback is for UX
     * surfaces, not control flow.
     */
    suspend fun enrich(
        snapshot: ScanSnapshot,
        reportProgress: suspend (booksEnriched: Int, chaptersEnriched: Int, currentFolder: String?) -> Unit,
    ): ScanSnapshot = withContext(Dispatchers.IO) {
        var chapters = 0
        val enriched = snapshot.books.mapIndexed { index, book ->
            // Pre-emit so the banner reports the *upcoming* folder before the
            // (potentially slow) per-file metadata read.
            runCatching { reportProgress(index, chapters, book.title) }
            val out = enrichBook(book)
            chapters += out.chapters.size
            runCatching { reportProgress(index + 1, chapters, book.title) }
            out
        }
        ScanSnapshot(enriched)
    }

    private suspend fun enrichBook(book: ScannedBook): ScannedBook {
        // Phase I.8: SingleFile books — one structural chapter that spans the whole file —
        // get expanded with embedded chapter markers when the parser finds any. We always
        // run the standard per-chapter metadata pass; for marks-expanded books all generated
        // chapters share the same fileUri, so file metadata (duration, cover, author) is
        // resolved once and broadcast.
        val expanded = maybeExpandSingleFileChapters(book)

        val perChapterMetadata: List<FileMetadata?> = if (expanded.chapters.isNotEmpty() &&
            expanded.chapters.all { it.fileUri == expanded.chapters.first().fileUri }
        ) {
            // All chapters point at the same file — one analyzer call, broadcast result.
            val one = expanded.chapters.first().fileUri
                ?.let { runCatching { Uri.parse(it) }.getOrNull() }
                ?.let { mediaAnalyzer.extract(it) }
            List(expanded.chapters.size) { one }
        } else {
            expanded.chapters.map { chapter ->
                chapter.fileUri?.let { uriString ->
                    runCatching { Uri.parse(uriString) }.getOrNull()?.let { mediaAnalyzer.extract(it) }
                }
            }
        }

        // If chapters were generated from embedded marks, durations come from positionInBookMs
        // deltas (last chapter duration = file duration - last mark position) and we should
        // NOT overwrite with the per-file duration the analyzer reports (it'd assign the full
        // file length to every row).
        val marksDrivenDurations = expanded.chapters.size > 1 &&
            expanded.chapters.all { it.fileUri == expanded.chapters.first().fileUri }

        val enrichedChapters: List<ScannedChapter>
        if (marksDrivenDurations) {
            val fileDuration = perChapterMetadata.firstNotNullOfOrNull { it?.durationMs } ?: 0L
            enrichedChapters = expanded.chapters.mapIndexed { i, chapter ->
                val nextStart = expanded.chapters.getOrNull(i + 1)?.positionInBookMs ?: fileDuration
                val duration = (nextStart - chapter.positionInBookMs).coerceAtLeast(0L)
                chapter.copy(durationMs = duration)
            }
        } else {
            var cumulative = 0L
            enrichedChapters = expanded.chapters.zip(perChapterMetadata) { chapter, metadata ->
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
        }

        // Roll up book-level fields. Author preference: scan-supplied → first per-chapter author.
        val rolledAuthor = expanded.author ?: perChapterMetadata.firstNotNullOfOrNull { it?.author }
        // Duration: marks-driven means chapters share one fileUri, so total = file duration;
        // otherwise sum the per-chapter durations the analyzer reported.
        val rolledDuration = if (marksDrivenDurations) {
            perChapterMetadata.firstNotNullOfOrNull { it?.durationMs }
                ?: enrichedChapters.sumOf { it.durationMs }
        } else {
            enrichedChapters.sumOf { it.durationMs }
        }
        // Cover-art Phase A local-first preference order: folder-level bytes from
        // SafLibraryScanner / FolderCoverFinder win (already on `book.embeddedCoverBytes` if
        // the scanner found a sidecar image); only fall back to per-chapter embedded extraction
        // when the scanner didn't already find a sidecar. Mirrors Voice's order (folder > embedded).
        val resolvedCover = expanded.embeddedCoverBytes
            ?: perChapterMetadata.firstNotNullOfOrNull { it?.embeddedCoverBytes }
            ?: extractCoverFromFirstFiveChapters(expanded)

        return expanded.copy(
            chapters = enrichedChapters,
            author = rolledAuthor,
            durationMs = rolledDuration,
            embeddedCoverBytes = resolvedCover,
        )
    }

    /**
     * Cover-art Phase A.3 fallback: when [Media3MediaAnalyzer] / [MediaMetadataRetriever]
     * returns no embedded cover for any of the per-chapter calls, try the dedicated
     * container-aware extractors (MP4 `covr`, Matroska `AttachedFile`, MP3 `APIC`) against
     * the first 5 chapter files. Voice's `scanForEmbeddedCover` pattern. First success wins.
     *
     * Deduplicates by URI so a SingleFile book whose 5 "chapters" all point at the same file
     * only opens the source once.
     */
    private suspend fun extractCoverFromFirstFiveChapters(book: ScannedBook): ByteArray? {
        val dispatcher = coverExtractorDispatcher ?: return null
        val seen = LinkedHashSet<String>()
        for (chapter in book.chapters.take(5)) {
            val uriString = chapter.fileUri ?: continue
            if (!seen.add(uriString)) continue
            val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: continue
            val bytes = runCatching {
                dispatcher.extract(uri, mimeType = null, fileName = book.relativePath)
            }.getOrNull()
            if (bytes != null) return bytes
        }
        return null
    }

    /**
     * Phase I.8: if [book] is a SingleFile book (one chapter, that chapter spans the file)
     * and the embedded-chapter parser returns marks, produce a new [ScannedBook] with one
     * [ScannedChapter] per mark. Chapter ids are derived via the same `chapterIdFor` scheme
     * the structural scanner uses so re-scans remain stable.
     */
    private suspend fun maybeExpandSingleFileChapters(book: ScannedBook): ScannedBook {
        val parser = chapterParser ?: return book
        if (book.chapters.size != 1) return book
        val sole = book.chapters.first()
        val uriString = sole.fileUri ?: return book
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return book
        val marks: List<ChapterMark> = parser.parse(uri, mimeType = null, fileName = book.relativePath)
        if (marks.isEmpty()) return book
        // Sort by position defensively; some producers emit unordered chapters.
        val sorted = marks.sortedBy { it.positionMs }
        val newChapters = sorted.mapIndexed { index, mark ->
            ScannedChapter(
                chapterId = SafLibraryScanner.chapterIdFor(book.bookId, index),
                chapterIndex = index,
                title = mark.title.ifBlank { "Chapter ${index + 1}" },
                fileUri = uriString,
                positionInBookMs = mark.positionMs,
            )
        }
        return book.copy(chapters = newChapters)
    }
}
