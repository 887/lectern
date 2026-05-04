package com.eight87.whisperboy.data.library

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database holding the library cache: books / chapters / bookmarks.
 *
 * Schema export is on (`schemas/com.eight87.whisperboy.data.library.LibraryDatabase/<version>.json`)
 * — committed to the repo so future migrations can diff. **No `fallbackToDestructiveMigration`** —
 * any v1→v2+ schema change must ship a migration.
 *
 * Built once in [com.eight87.whisperboy.AppGraph] via `Room.databaseBuilder`.
 */
@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        BookmarkEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    abstract fun chapterDao(): ChapterDao

    abstract fun bookmarkDao(): BookmarkDao
}
