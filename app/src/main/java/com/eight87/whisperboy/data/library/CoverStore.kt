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

    /**
     * Returns every cover file basename currently on disk in [coversDir]. Tmp files (`*.tmp`
     * left over from a crashed [writeCover]) are excluded. Used by [gcOrphans] to compute
     * the orphan set; also useful for tests + diagnostics.
     */
    suspend fun listBookIdsOnDisk(): Set<String> {
        val files = coversDir.listFiles() ?: return emptySet()
        return files.asSequence()
            .filter { it.isFile && !it.name.endsWith(".tmp") }
            .map { it.name }
            .toSet()
    }

    /**
     * Deletes every cover file on disk whose basename is not in [activeBookIds]. Returns the
     * number of files deleted. Wired into [com.eight87.whisperboy.data.library.AndroidLibraryRescanCoordinator]
     * at the END of a successful scan so orphans from `forgetBook`, `markRootInactive`, and
     * scan-removed books are reaped on the next scan. Disk-leak proof — even if `deleteCover`
     * failed earlier or the process died mid-call, the orphan is collected here.
     *
     * Tmp files (`<bookId>.tmp`) are also reaped — they are by definition partial writes from a
     * crashed [writeCover].
     */
    suspend fun gcOrphans(activeBookIds: Set<String>): Int {
        val files = coversDir.listFiles() ?: return 0
        var deleted = 0
        for (file in files) {
            if (!file.isFile) continue
            if (file.name.endsWith(".tmp") || file.name !in activeBookIds) {
                if (file.delete()) deleted += 1
            }
        }
        return deleted
    }
}
