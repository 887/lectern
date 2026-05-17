package com.eight87.whisperboy.data.library

/**
 * Narrow data interface (Phase R.A pattern) for the write side of the scan pipeline.
 *
 * Phase D.4's [LibraryRepository] is the only implementation. Phase D.5's rescan triggers
 * (manual / on-foreground / on-root-add-or-remove) call this method on `Dispatchers.IO`.
 *
 * Separating the write surface from the three read interfaces ([BookSource] /
 * [ChapterSource] / [BookmarkSource]) keeps composables strictly read-only and lets the
 * trigger layer take a one-method handle without seeing the rest of the repo.
 */
interface ScanWriter {

    /**
     * Apply [snapshot] to the cache: upsert incoming books and their chapters in one Room
     * transaction, soft-delete books that were active before but are missing from the
     * incoming set (`active = false` — preserves their bookmarks + per-book settings for a
     * folder re-add to restore), and write any embedded cover bytes to disk.
     */
    suspend fun applyScan(snapshot: ScanSnapshot)

    /**
     * Streaming-scan structural batch: writes [books] to Room with `enriched = false` so they
     * appear in [BookSource.observeBooks] immediately, before per-chapter media analysis runs.
     * Each book's [ScannedBook.durationMs] is preserved as 0 here; the worker pool calls
     * [applyBookEnrichment] later to fill in durations + author roll-up + cover. Position /
     * playback knobs / custom-cover flags on existing rows are preserved (R.F.4 — uses
     * [BookDao.updateStructuralPartial]).
     *
     * Does NOT sweep root-inactive books (snapshot-level invariant); the coordinator runs the
     * inactive sweep once at the end of discovery via [sweepInactiveRoots].
     */
    suspend fun applyBookBatch(books: List<ScannedBook>)

    /**
     * Streaming-scan enrichment landing for a single book. [enrichedBook] is the result of
     * [LibraryScannerEnrichment.enrichBook]; this method writes the derived duration / author /
     * cover / per-chapter durations to Room and flips `enriched = true`. Does NOT touch
     * position / playback-knob fields.
     */
    suspend fun applyBookEnrichment(enrichedBook: ScannedBook)

    /**
     * Streaming-scan equivalent of the per-root soft-delete sweep that lived inside
     * [applyScan]. The coordinator calls this once discovery has finished, passing the
     * `treeUri`s actually walked plus the set of book ids the streaming pipeline observed.
     * Books from those roots not in [seenBookIds] are flipped to `active = 0`.
     */
    suspend fun sweepInactiveRoots(touchedRoots: Set<String>, seenBookIds: Set<String>)
}
