package com.eight87.whisperboy.data.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Walks each [LibraryRoot]'s SAF tree via [CachedDocumentFile] and dispatches by
 * [FolderType] via exhaustive `when`. Returns a [ScanSnapshot] for Phase D.4's
 * `LibraryRepository.applyScan` to write into Room in one transaction.
 *
 * Phase D.2 fills the structural fields — book id, chapter ids, file URIs, chapter order.
 * Phase D.3's `MediaAnalyzer` will run *after* the structural walk to fill durations,
 * titles from metadata, author from metadata, and embedded cover bytes; that enrichment
 * also drives `positionInBookMs` since cumulative position requires per-chapter durations.
 *
 * Cover-art Phase A also rides this walk: while we're already inside each book's folder we
 * call [FolderCoverFinder] to look for a sidecar `cover.jpg` / `folder.jpg` / `albumart.png`
 * etc. and stash the bytes on [ScannedBook.embeddedCoverBytes]. This is local-first preference
 * #2 from `docs/plans/cover-art.md` — between "saved cover already on disk" (handled by
 * [LibraryRepository.applyScan] only writing when bytes are present) and "extracted from the
 * audio container" (filled later by [LibraryScannerEnrichment]). Folder-found bytes win over
 * embedded bytes because enrichment only fills the field if the scanner left it null.
 *
 * Stable identifiers: [bookIdFor] hashes `<treeUriString>#<relativePath>` so the same book
 * at the same path keeps its row across rescans. [chapterIdFor] hashes `<bookId>@<index>`
 * so re-running the scan yields the same chapter ids when the file order is unchanged.
 */
class SafLibraryScanner(
    private val context: Context,
    private val folderCoverFinder: FolderCoverFinder = FolderCoverFinder(),
    /**
     * Phase K.4 sub-screen — `() -> Set<String>` of user-disabled extensions (lowercase),
     * snapshotted once at the top of each scan so the filter stays consistent across
     * the walk. Default `{ emptySet() }` keeps the existing test paths trivial.
     */
    private val disabledExtensionsProvider: () -> Set<String> = { emptySet() },
) : LibraryScanner {

    override suspend fun scan(
        roots: List<LibraryRoot>,
        onProgress: suspend (booksFound: Int, chaptersFound: Int, currentFolder: String?) -> Unit,
    ): ScanSnapshot = scanStreaming(roots, onProgress) { /* discard per-book emissions */ }

    override suspend fun scanStreaming(
        roots: List<LibraryRoot>,
        onProgress: suspend (booksFound: Int, chaptersFound: Int, currentFolder: String?) -> Unit,
        onBookDiscovered: suspend (ScannedBook) -> Unit,
    ): ScanSnapshot = withContext(Dispatchers.IO) {
        val disabled = disabledExtensionsProvider()
        val accumulated = mutableListOf<ScannedBook>()
        // Mutable cumulative counters threaded into the per-folder helper so callers see the
        // banner counts tick up *as* the SAF tree is walked — not only once the whole structural
        // pass settles.
        var booksFound = 0
        var chaptersFound = 0
        suspend fun emit(currentFolder: String?) {
            runCatching { onProgress(booksFound, chaptersFound, currentFolder) }
        }
        for (root in roots) {
            scanRoot(root, disabled) { discovered, currentFolder ->
                accumulated += discovered
                booksFound += discovered.size
                chaptersFound += discovered.sumOf { it.chapters.size }
                // Emit each discovered book to the streaming consumer (channel-fed worker pool
                // in `AndroidLibraryRescanCoordinator`) before reporting progress.
                for (book in discovered) {
                    runCatching { onBookDiscovered(book) }
                }
                emit(currentFolder)
            }
        }
        ScanSnapshot(accumulated)
    }

    /**
     * Phase P.8 — cheap "is this tree the same as last scan?" probe.
     *
     * Walks [treeUri]'s tree shallow via [DocumentsContract.buildChildDocumentsUriUsingTree],
     * summing the document count and tracking max `COLUMN_LAST_MODIFIED`. Returns the pair
     * as a [LibraryFingerprint]; the coordinator compares against the previously-persisted
     * fingerprint and skips the full structural walk when unchanged.
     *
     * Returns `null` on [SecurityException] (revoked SAF permission) or any other failure —
     * the coordinator treats null as "permission revoked, walk anyway so the structural pass
     * can surface the failure into [LibraryHealth.unreadableRoots]".
     *
     * Cost note: this is one shallow `query()` per root, not a full tree walk. Even a 500-book
     * library returns in milliseconds because we stop at the root's immediate children plus
     * the metadata columns; the full content walk in [scan] is the expensive path we're
     * trying to skip.
     */
    suspend fun computeFingerprint(treeUri: Uri): LibraryFingerprint? = withContext(Dispatchers.IO) {
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            var count = 0
            var maxMtime = 0L
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val mtimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    count += 1
                    if (mtimeIdx >= 0 && !cursor.isNull(mtimeIdx)) {
                        val m = cursor.getLong(mtimeIdx)
                        if (m > maxMtime) maxMtime = m
                    }
                }
            } ?: return@withContext null
            LibraryFingerprint(documentCount = count, maxMtime = maxMtime)
        } catch (se: SecurityException) {
            Log.w("whisperboy.scan", "FINGERPRINT_SECURITY treeUri=$treeUri: ${se.message}")
            null
        } catch (t: Throwable) {
            Log.w("whisperboy.scan", "FINGERPRINT_FAILED treeUri=$treeUri: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    /**
     * Per-root walker. [onBookDiscovered] is invoked synchronously each time a [ScannedBook]
     * is produced, paired with a human-readable "current folder" hint (the folder/file name
     * currently being scanned). The hint is what the in-library progress banner surfaces so
     * the user sees motion during the otherwise opaque SAF traversal.
     */
    private suspend fun scanRoot(
        root: LibraryRoot,
        disabled: Set<String> = emptySet(),
        onBookDiscovered: suspend (books: List<ScannedBook>, currentFolder: String?) -> Unit = { _, _ -> },
    ) {
        val tree = DocumentFile.fromTreeUri(context, root.treeUri) ?: return
        val cached = CachedDocumentFile(tree)
        when (root.folderType) {
            FolderType.SingleFile -> {
                val books = scanSingleFile(root, cached, disabled)
                if (books.isNotEmpty()) onBookDiscovered(books, cached.name)
            }
            FolderType.SingleFolder -> {
                val books = scanFolderAsBook(
                    root = root,
                    folder = cached,
                    relativePath = "",
                    author = null,
                    disabled = disabled,
                )
                if (books.isNotEmpty()) onBookDiscovered(books, cached.name)
            }
            FolderType.Root -> cached.children
                .filter { it.isDirectory }
                .forEach { sub ->
                    val books = scanFolderAsBook(
                        root = root,
                        folder = sub,
                        relativePath = sub.name ?: "",
                        author = null,
                        disabled = disabled,
                    )
                    if (books.isNotEmpty()) onBookDiscovered(books, sub.name)
                }
            FolderType.Author -> cached.children
                .filter { it.isDirectory }
                .forEach { authorFolder ->
                    authorFolder.children
                        .filter { it.isDirectory }
                        .forEach { bookFolder ->
                            val books = scanFolderAsBook(
                                root = root,
                                folder = bookFolder,
                                relativePath = "${authorFolder.name ?: ""}/${bookFolder.name ?: ""}",
                                author = authorFolder.name,
                                disabled = disabled,
                            )
                            if (books.isNotEmpty()) {
                                onBookDiscovered(books, "${authorFolder.name ?: ""}/${bookFolder.name ?: ""}")
                            }
                        }
                }
        }
    }

    private fun scanSingleFile(
        root: LibraryRoot,
        file: CachedDocumentFile,
        disabled: Set<String> = emptySet(),
    ): List<ScannedBook> {
        if (!SupportedAudioFormats.isAudioFile(file, disabled)) return emptyList()
        val name = file.name ?: return emptyList()
        val bookId = bookIdFor(root.treeUri.toString(), name)
        val chapter = ScannedChapter(
            chapterId = chapterIdFor(bookId, 0),
            chapterIndex = 0,
            title = name.substringBeforeLast('.'),
            fileUri = file.uri.toString(),
            positionInBookMs = 0L,
        )
        return listOf(
            ScannedBook(
                bookId = bookId,
                treeUriString = root.treeUri.toString(),
                relativePath = name,
                title = name.substringBeforeLast('.'),
                chapters = listOf(chapter),
            )
        )
    }

    /**
     * Treat [folder] as a single book whose chapters are the audio files directly inside it,
     * sorted alphabetically (`String.lowercase()` for case-insensitive natural-ish ordering;
     * a stricter natural sort with multi-digit run handling can be introduced if a real-world
     * collection demands it).
     */
    private fun scanFolderAsBook(
        root: LibraryRoot,
        folder: CachedDocumentFile,
        relativePath: String,
        author: String?,
        disabled: Set<String> = emptySet(),
    ): List<ScannedBook> {
        val audioChildren = folder.children
            .filter { SupportedAudioFormats.isAudioFile(it, disabled) }
            .sortedBy { (it.name ?: "").lowercase() }
        if (audioChildren.isEmpty()) return emptyList()

        val effectiveRelativePath = relativePath.ifEmpty { folder.name ?: "" }
        val bookId = bookIdFor(root.treeUri.toString(), effectiveRelativePath)
        val chapters = audioChildren.mapIndexed { index, file ->
            ScannedChapter(
                chapterId = chapterIdFor(bookId, index),
                chapterIndex = index,
                title = file.name?.substringBeforeLast('.'),
                fileUri = file.uri.toString(),
                // positionInBookMs stays 0 here — Phase D.3's MediaAnalyzer fills durations,
                // and Phase D.4's applyScan computes the cumulative offsets in one pass.
                positionInBookMs = 0L,
            )
        }

        // Cover-art Phase A local-first preference #2: look for a folder-level sidecar cover
        // image (`cover.jpg`, `folder.png`, `albumart.webp`, etc.) alongside the audio. Bytes
        // are read inline via contentResolver — cheap, and we're already on Dispatchers.IO
        // from `scan`'s `withContext`. A broken stream / missing file becomes `null` and
        // falls through to embedded extraction in Phase D.3 enrichment.
        val folderCoverBytes: ByteArray? = folderCoverFinder.findCover(folder)?.let { coverDoc ->
            runCatching {
                context.contentResolver.openInputStream(coverDoc.uri)?.use { it.readBytes() }
            }.getOrNull()
        }

        return listOf(
            ScannedBook(
                bookId = bookId,
                treeUriString = root.treeUri.toString(),
                relativePath = effectiveRelativePath,
                title = folder.name ?: effectiveRelativePath,
                author = author,
                chapters = chapters,
                embeddedCoverBytes = folderCoverBytes,
            )
        )
    }

    companion object {
        fun bookIdFor(treeUriString: String, relativePath: String): String =
            sha256("$treeUriString#$relativePath")

        fun chapterIdFor(bookId: String, index: Int): String =
            sha256("$bookId@$index")

        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
