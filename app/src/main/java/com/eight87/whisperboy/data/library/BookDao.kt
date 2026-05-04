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

    @Query("DELETE FROM books WHERE bookId = :id")
    suspend fun deleteById(id: String)
}
