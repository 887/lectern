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
}
