package com.eight87.whisperboy.data.library

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-placed bookmark, optionally tagged with which chapter it landed in. Auto-bookmarks
 * created when the sleep timer fires (Phase G.5) set [setBySleepTimer] = true so the bookmark
 * list can render them with a clock badge.
 *
 * `bookId` cascades on book delete; `chapterId` set-nulls if a chapter row disappears (e.g. a
 * scan re-derives different markers for the same book) — the bookmark survives and pins to the
 * book-level position.
 */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["bookId"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["chapterId"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["chapterId"]),
    ],
)
data class BookmarkEntity(
    @PrimaryKey val bookmarkId: String,
    val bookId: String,
    val chapterId: String? = null,
    val title: String? = null,
    @ColumnInfo(name = "position_in_book_ms") val positionInBookMs: Long,
    val addedAt: Long,
    val setBySleepTimer: Boolean = false,
)
