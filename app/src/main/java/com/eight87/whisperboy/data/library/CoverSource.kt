package com.eight87.whisperboy.data.library

import androidx.room.TypeConverter

/**
 * Where a book's cover came from. Phase A.6 (`cover-art.md`) — the user can override the
 * scan-derived cover via "Use custom cover from device"; once they do, subsequent rescans
 * MUST NOT overwrite their pick. [LibraryRepository.applyScan] consults this flag per-book.
 *
 * Stored as a plain `TEXT` column in `books.coverSource` (defaults to `Scanned` for rows
 * that pre-date schema v3 — see `MIGRATION_2_3`).
 */
enum class CoverSource {
    /** The scanner set this cover (folder sidecar image, embedded tag, or null = no cover). */
    Scanned,

    /** The user explicitly picked this cover from a SAF document picker. Survives rescans. */
    Custom,
}

/**
 * Room converter pair for [CoverSource]. Registered on [LibraryDatabase] via
 * `@TypeConverters(CoverSourceConverter::class)`.
 */
class CoverSourceConverter {

    @TypeConverter
    fun fromCoverSource(value: CoverSource): String = value.name

    @TypeConverter
    fun toCoverSource(value: String): CoverSource =
        runCatching { CoverSource.valueOf(value) }.getOrDefault(CoverSource.Scanned)
}
