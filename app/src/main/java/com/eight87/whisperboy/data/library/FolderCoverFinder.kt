package com.eight87.whisperboy.data.library

/**
 * Looks for a folder-level cover image next to the audio files of a book.
 *
 * Convention: when an audio file has no embedded cover, scanners commonly drop a
 * `cover.jpg` / `cover.png` / `folder.jpg` / `folder.png` alongside the audio. This helper
 * walks the [CachedDocumentFile.children] of a book's folder and returns the first match.
 *
 * Match is case-insensitive on the filename. Returns the first hit in iteration order — the
 * caller (Phase D.3's [LibraryScannerEnrichment]) treats any match as authoritative for that
 * book.
 */
class FolderCoverFinder {

    fun findCover(folder: CachedDocumentFile): CachedDocumentFile? =
        folder.children.firstOrNull { file ->
            if (!file.isFile) return@firstOrNull false
            val name = file.name?.lowercase() ?: return@firstOrNull false
            name in COVER_FILENAMES
        }

    private companion object {
        val COVER_FILENAMES: Set<String> = setOf(
            "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
            "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
            "albumart.jpg", "albumart.png",
        )
    }
}
