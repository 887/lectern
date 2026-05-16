package com.eight87.whisperboy.data.library

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 3,
    exportSchema = true,
)
@TypeConverters(CoverSourceConverter::class)
abstract class LibraryDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    abstract fun chapterDao(): ChapterDao

    abstract fun bookmarkDao(): BookmarkDao
}

/**
 * v1 → v2: add `completedAt` column to `books` for Phase E.5's "Mark completed" action.
 * Nullable Long; existing rows default to null (book not completed).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN completedAt INTEGER")
    }
}

/**
 * v2 → v3: add `coverSource` column to `books` for Phase A.6's "Use custom cover from device".
 * `TEXT NOT NULL DEFAULT 'Scanned'` — existing rows default to scanner-owned covers so a
 * follow-up rescan can still replace them; only covers the user explicitly picks land as
 * `'Custom'` and survive subsequent rescans.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN coverSource TEXT NOT NULL DEFAULT 'Scanned'")
    }
}
