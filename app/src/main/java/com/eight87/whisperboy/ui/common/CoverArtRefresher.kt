package com.eight87.whisperboy.ui.common

import android.content.Context
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import java.io.File

/**
 * cover-art.md Phase D.1 — best-effort Coil cache invalidation for a single book.
 *
 * Coil 3 keys cache entries on the model. The [CoverArt] composable uses
 * `File(coverPath)` as the model, so the memory-cache key is the file path string
 * and the disk-cache key follows the same convention. When the on-disk bytes at
 * `<filesDir>/covers/<bookId>` get rewritten (Phase A.6 manual cover, Phase B+C
 * search-online cover, or a future rescan that overwrites a `Scanned` cover), Coil's
 * in-memory bitmap is still served from the cache and the UI shows stale art.
 *
 * Dropping the cache here is the cheapest possible fix: next compose, [CoverArt]'s
 * Coil request misses on both layers and re-decodes from disk. The on-disk file
 * itself is left alone — only the cached decode is dropped.
 *
 * Lives in the UI layer (not in `data/library/`) because the [SingletonImageLoader]
 * is an Android-only singleton initialised lazily by Compose; the data layer
 * shouldn't reach for it. This keeps `BookSource` / `LibraryRepository` clean.
 */
fun refreshCoverArt(context: Context, coverPath: String?) {
    if (coverPath.isNullOrBlank()) return
    val loader = SingletonImageLoader.get(context.applicationContext)

    // Memory-cache key. AsyncImage / SubcomposeAsyncImage build the key from the
    // model's `toString()`; for a File model that's the absolute path.
    loader.memoryCache?.remove(MemoryCache.Key(coverPath))
    // Also try the toString form of the File (used when Coil canonicalises to a
    // file:// URL internally — keys vary by Coil version, so we try both).
    loader.memoryCache?.remove(MemoryCache.Key(File(coverPath).toString()))

    // Disk cache uses the same string. `remove` returns false silently if absent,
    // which is exactly the behaviour we want.
    loader.diskCache?.remove(coverPath)
}
