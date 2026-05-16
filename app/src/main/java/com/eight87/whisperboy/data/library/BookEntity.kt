package com.eight87.whisperboy.data.library

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted-state row for a single audiobook found in one of the user's [LibraryRoot]s.
 *
 * The [bookId] is a stable hash of the source location (`<treeUri>#<relativePath>`) so the same
 * book at the same path keeps its bookmarks / position / per-book settings across rescans.
 *
 * Per-book playback settings (speed, skip-silence, gain) live on this row, not in DataStore —
 * Voice's pattern, validated. Settings inherit from global defaults when a book is first scanned;
 * subsequent edits write to the row, not back to the defaults.
 */
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["treeUriString"]),
        Index(value = ["lastPlayedAt"]),
    ],
)
data class BookEntity(
    @PrimaryKey val bookId: String,

    /** The [LibraryRoot] this book was scanned from (the canonical persistable URI). */
    val treeUriString: String,

    /** Path within the root tree, identifies the file or sub-folder containing the book. */
    val relativePath: String,

    val title: String,
    val author: String? = null,

    /** Total runtime in milliseconds across all chapters. */
    @ColumnInfo(name = "duration_ms") val durationMs: Long,

    /** Path to the cached cover bytes in the app's `files/covers/` directory; null when no cover. */
    val coverPath: String? = null,

    /**
     * Where [coverPath] came from. `Scanned` covers are owned by the scanner and may be
     * overwritten by a later rescan; `Custom` covers were explicitly picked by the user
     * (Phase A.6 in `cover-art.md`) and must survive rescans. Defaults to `Scanned`.
     */
    val coverSource: CoverSource = CoverSource.Scanned,

    /** Resume position. */
    val currentChapterIndex: Int = 0,
    @ColumnInfo(name = "position_in_chapter_ms") val positionInChapterMs: Long = 0L,

    /** Per-book playback settings. */
    val speed: Float = 1.0f,
    val skipSilenceEnabled: Boolean = false,
    @ColumnInfo(name = "gain_db") val gainDb: Float = 0.0f,

    /** Epoch ms; null means never played. Indexed so "Recently played" sort is cheap. */
    val lastPlayedAt: Long? = null,

    /**
     * Epoch ms when the user explicitly marked this book as completed via the long-press
     * action sheet (Phase E.5). `null` = not completed. Voice's pattern — explicit flag rather
     * than inferring from position-equals-duration; users want to mark books done before
     * actually playing to the literal end, and conversely sometimes the file ends a few
     * seconds short.
     */
    val completedAt: Long? = null,

    /**
     * Soft-delete flag. Set to `false` when the book disappears from the source on rescan;
     * preserves bookmarks and position so a re-add of the same folder restores everything.
     */
    val active: Boolean = true,
)
