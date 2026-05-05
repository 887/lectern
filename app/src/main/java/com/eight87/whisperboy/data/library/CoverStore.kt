package com.eight87.whisperboy.data.library

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores cover-art bytes on disk under `<filesDir>/covers/<bookId>`.
 *
 * Phase D.4's `applyScan` calls [writeCover] for each book whose enriched [ScannedBook]
 * carries `embeddedCoverBytes`; the returned absolute path is stored on
 * [BookEntity.coverPath]. Writes are atomic — bytes go to a `<bookId>.tmp` first, then
 * `renameTo` lands the final file. A failed mid-write doesn't leave a half-cover at the
 * canonical path.
 *
 * Path format: `<filesDir>/covers/<bookId>` — no extension. The file is opened by Phase E's
 * cover-loading composable via Coil (which sniffs JPEG/PNG/WebP from the bytes anyway).
 */
class CoverStore(context: Context) {

    private val coversDir: File = File(context.applicationContext.filesDir, "covers")

    init {
        coversDir.mkdirs()
    }

    /**
     * Atomically write [bytes] for [bookId]. Returns the absolute path on disk.
     *
     * Strategy: write to `<bookId>.tmp`, then `renameTo` the final name. If the rename fails
     * (some filesystems on weird mount points), fall back to overwrite-then-delete-tmp.
     */
    suspend fun writeCover(bookId: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val target = File(coversDir, bookId)
        val tmp = File(coversDir, "$bookId.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        target.absolutePath
    }

    /** Best-effort delete of the cover for a given book id. */
    suspend fun deleteCover(bookId: String) = withContext(Dispatchers.IO) {
        File(coversDir, bookId).delete()
        Unit
    }

    /** Path that [writeCover] will use for a given book id (stable, deterministic). */
    fun pathFor(bookId: String): String = File(coversDir, bookId).absolutePath
}
