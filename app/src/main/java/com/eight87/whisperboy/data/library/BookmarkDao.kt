package com.eight87.whisperboy.data.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query(
        """
        SELECT * FROM bookmarks
        WHERE bookId = :bookId
        ORDER BY position_in_book_ms
        """
    )
    fun observeForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Query("UPDATE bookmarks SET title = :title WHERE bookmarkId = :id")
    suspend fun rename(id: String, title: String?)

    @Query("DELETE FROM bookmarks WHERE bookmarkId = :id")
    suspend fun deleteById(id: String)
}
