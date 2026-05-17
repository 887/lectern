package com.eight87.whisperboy.data.library

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books WHERE active = 1 ORDER BY title COLLATE NOCASE")
    fun observeActive(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE bookId = :id LIMIT 1")
    fun observeById(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE bookId = :id LIMIT 1")
    suspend fun findById(id: String): BookEntity?

    @Query(
        """
        SELECT * FROM books
        WHERE active = 1
          AND (title LIKE '%' || :query || '%' COLLATE NOCASE
               OR author LIKE '%' || :query || '%' COLLATE NOCASE)
        ORDER BY title COLLATE NOCASE
        """
    )
    suspend fun search(query: String): List<BookEntity>

    @Upsert
    suspend fun upsert(book: BookEntity)

    @Upsert
    suspend fun upsertAll(books: List<BookEntity>)

    @Query("UPDATE books SET active = 0 WHERE treeUriString = :treeUriString")
    suspend fun markRootInactive(treeUriString: String)

    @Query("UPDATE books SET active = 0 WHERE bookId IN (:ids)")
    suspend fun markInactiveByIds(ids: List<String>)

    @Query("UPDATE books SET completedAt = :timestamp WHERE bookId = :id")
    suspend fun setCompletedAt(id: String, timestamp: Long?)

    @Query("UPDATE books SET coverPath = :path WHERE bookId = :id")
    suspend fun setCoverPath(id: String, path: String?)

    @Query("UPDATE books SET coverSource = :source WHERE bookId = :id")
    suspend fun setCoverSource(id: String, source: CoverSource)

    @Query("UPDATE books SET lastPlayedAt = :timestamp WHERE bookId = :id")
    suspend fun setLastPlayedAt(id: String, timestamp: Long?)

    /**
     * Phase P.7 — event-driven position save. Writes the (chapter, in-chapter position,
     * last-played-at) tuple atomically. Voice's "experimental playback persistence"
     * pattern: save on real events (transition / pause / background / shutdown), not on
     * a 1Hz timer. Battery-friendly and survives process death without an in-flight DB
     * write being orphaned.
     */
    @Query(
        """
        UPDATE books
           SET currentChapterIndex = :chapterIndex,
               position_in_chapter_ms = :positionMs,
               lastPlayedAt = :lastPlayedAt
         WHERE bookId = :id
        """
    )
    suspend fun setLastPlayedPosition(id: String, chapterIndex: Int, positionMs: Long, lastPlayedAt: Long)

    @Query("UPDATE books SET speed = :speed WHERE bookId = :id")
    suspend fun setSpeed(id: String, speed: Float)

    @Query("UPDATE books SET skipSilenceEnabled = :enabled WHERE bookId = :id")
    suspend fun setSkipSilence(id: String, enabled: Boolean)

    @Query("UPDATE books SET gain_db = :gainDb WHERE bookId = :id")
    suspend fun setGainDb(id: String, gainDb: Float)

    @Query("DELETE FROM books WHERE bookId = :id")
    suspend fun deleteById(id: String)
}
