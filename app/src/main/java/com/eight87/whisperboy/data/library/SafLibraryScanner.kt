package com.eight87.whisperboy.data.library

import android.content.Context
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
) : LibraryScanner {

    override suspend fun scan(roots: List<LibraryRoot>): ScanSnapshot = withContext(Dispatchers.IO) {
        val books = roots.flatMap { scanRoot(it) }
        ScanSnapshot(books)
    }

    private fun scanRoot(root: LibraryRoot): List<ScannedBook> {
        val tree = DocumentFile.fromTreeUri(context, root.treeUri) ?: return emptyList()
        val cached = CachedDocumentFile(tree)
        return when (root.folderType) {
            FolderType.SingleFile -> scanSingleFile(root, cached)
            FolderType.SingleFolder -> scanFolderAsBook(
                root = root,
                folder = cached,
                relativePath = "",
                author = null,
            )
            FolderType.Root -> cached.children
                .filter { it.isDirectory }
                .flatMap { sub ->
                    scanFolderAsBook(
                        root = root,
                        folder = sub,
                        relativePath = sub.name ?: "",
                        author = null,
                    )
                }
            FolderType.Author -> cached.children
                .filter { it.isDirectory }
                .flatMap { authorFolder ->
                    authorFolder.children
                        .filter { it.isDirectory }
                        .flatMap { bookFolder ->
                            scanFolderAsBook(
                                root = root,
                                folder = bookFolder,
                                relativePath = "${authorFolder.name ?: ""}/${bookFolder.name ?: ""}",
                                author = authorFolder.name,
                            )
                        }
                }
        }
    }

    private fun scanSingleFile(root: LibraryRoot, file: CachedDocumentFile): List<ScannedBook> {
        if (!SupportedAudioFormats.isAudioFile(file)) return emptyList()
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
    ): List<ScannedBook> {
        val audioChildren = folder.children
            .filter { SupportedAudioFormats.isAudioFile(it) }
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
