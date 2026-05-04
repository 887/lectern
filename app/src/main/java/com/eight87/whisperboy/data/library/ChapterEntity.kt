package com.eight87.whisperboy.data.library

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One chapter inside a [BookEntity]. Chapters are either:
 *
 * - **Separate audio files** (the SingleFolder / Root / Author cases): [fileUri] is set,
 *   [positionInBookMs] is the cumulative offset where this chapter starts in the book's overall
 *   timeline, [durationMs] is the file's duration.
 * - **Embedded chapter markers within a single file** (the SingleFile / M4B case): [fileUri] is
 *   null (the parent book's file is the source), [positionInBookMs] is the chapter mark's start,
 *   [durationMs] is the span until the next mark or end of file.
 *
 * Phase D.1 ships the schema; Phase D.2's scanner populates separate-file chapters; Phase I
 * extracts embedded chapter markers from M4B / Matroska and writes them as rows here.
 */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["bookId"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["bookId", "chapterIndex"], unique = true),
        Index(value = ["bookId"]),
    ],
)
data class ChapterEntity(
    @PrimaryKey val chapterId: String,
    val bookId: String,
    val chapterIndex: Int,
    val title: String? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,

    /** File URI for separate-file chapters; null for embedded-marker chapters. */
    val fileUri: String? = null,

    /** Cumulative offset (ms) where this chapter starts in the book's overall timeline. */
    @ColumnInfo(name = "position_in_book_ms") val positionInBookMs: Long,
)
