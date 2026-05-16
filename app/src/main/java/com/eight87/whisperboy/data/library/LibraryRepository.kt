package com.eight87.whisperboy.data.library

import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Concrete implementation of every narrow data interface in `data/library/`. Lives behind
 * [BookSource] / [ChapterSource] / [BookmarkSource] / [ScanWriter] when handed out from
 * [com.eight87.whisperboy.AppGraph] — composables and ViewModels never see this concrete
 * type (R.A.2 pattern from `docs/plans/refactor-solid.md`).
 *
 * The class is `internal` so module-internal code can pass it around as a single object but
 * external callers can only use the narrow handles `AppGraph` exposes.
 */
internal class LibraryRepository(
    private val database: LibraryDatabase,
    private val coverStore: CoverStore,
) : BookSource, ChapterSource, BookmarkSource, ScanWriter {

    private val bookDao = database.bookDao()
    private val chapterDao = database.chapterDao()
    private val bookmarkDao = database.bookmarkDao()

    // ---- BookSource ----

    override fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeActive()

    override fun observeBook(id: String): Flow<BookEntity?> = bookDao.observeById(id)

    override suspend fun search(query: String): List<BookEntity> = bookDao.search(query)

    override suspend fun markCompleted(bookId: String) {
        bookDao.setCompletedAt(bookId, System.currentTimeMillis())
    }

    override suspend fun markNotStarted(bookId: String) {
        // "Not started" semantically clears both completion AND last-played; the book returns
        // to the NotStarted filter bucket. Position is left intact — re-marking played picks up
        // where the user was, which is friendlier than wiping progress.
        bookDao.setCompletedAt(bookId, null)
        bookDao.setLastPlayedAt(bookId, null)
    }

    override suspend fun forgetBook(bookId: String) {
        // Phase E.5 contract: "Forget" deletes the row + bookmarks (FK CASCADE handles chapters
        // and bookmarks). It does NOT delete the audio files — whisperboy never touches the
        // user's library on disk. On a subsequent rescan the book reappears with default state.
        database.withTransaction { bookDao.deleteById(bookId) }
        coverStore.deleteCover(bookId)
    }

    override suspend fun setCustomCover(bookId: String, bytes: ByteArray) {
        // Phase A.6 (`cover-art.md`): write the bytes through [CoverStore] (atomic tmp+rename),
        // then flip the cached row to point at the new path with `coverSource = Custom` so
        // `applyScan` leaves it alone on subsequent rescans.
        val path = coverStore.writeCover(bookId, bytes)
        bookDao.setCoverPath(bookId, path)
        bookDao.setCoverSource(bookId, CoverSource.Custom)
    }

    // ---- ChapterSource ----

    override fun observeChaptersForBook(bookId: String): Flow<List<ChapterEntity>> =
        chapterDao.observeForBook(bookId)

    override suspend fun chaptersFor(bookId: String): List<ChapterEntity> =
        chapterDao.chaptersFor(bookId)

    // ---- BookmarkSource ----

    override fun observeBookmarksForBook(bookId: String): Flow<List<BookmarkEntity>> =
        bookmarkDao.observeForBook(bookId)

    override suspend fun addBookmark(
        bookId: String,
        chapterId: String?,
        title: String?,
        positionInBookMs: Long,
        setBySleepTimer: Boolean,
    ) {
        bookmarkDao.insert(
            BookmarkEntity(
                bookmarkId = UUID.randomUUID().toString(),
                bookId = bookId,
                chapterId = chapterId,
                title = title,
                positionInBookMs = positionInBookMs,
                addedAt = System.currentTimeMillis(),
                setBySleepTimer = setBySleepTimer,
            )
        )
    }

    override suspend fun renameBookmark(id: String, title: String?) {
        bookmarkDao.rename(id, title)
    }

    override suspend fun deleteBookmark(id: String) {
        bookmarkDao.deleteById(id)
    }

    // ---- ScanWriter ----

    override suspend fun applyScan(snapshot: ScanSnapshot) {
        // 1. Land cover bytes on disk OUTSIDE the Room transaction. Long file IO inside the
        //    txn would hold the database lock; a rollback would also leave the on-disk file
        //    orphaned. Write first, then commit rows that reference the path.
        //
        //    Phase A.6 (`cover-art.md`): a per-book lookup respects user-picked custom covers
        //    — if the existing cached row has `coverSource = Custom`, keep its `coverPath`
        //    and tag the new row as `Custom` too, so the scanner-derived bytes (sidecar
        //    folder image / embedded tag) don't overwrite the user's pick. Acceptable to
        //    fan out per-book here: scans run infrequently and the typical library is small.
        val booksWithCoverPaths: List<Triple<ScannedBook, String?, CoverSource>> =
            snapshot.books.map { book ->
                val existing = bookDao.findById(book.bookId)
                if (existing?.coverSource == CoverSource.Custom) {
                    Triple(book, existing.coverPath, CoverSource.Custom)
                } else {
                    val coverPath = book.embeddedCoverBytes?.let { bytes ->
                        coverStore.writeCover(book.bookId, bytes)
                    } ?: book.coverPath
                    Triple(book, coverPath, CoverSource.Scanned)
                }
            }

        // 2. Group incoming book ids by treeUri so soft-delete sweeps per touched root.
        //    Pattern: for each touched root, mark every active book from that root inactive,
        //    then upsert the incoming ones (which sets `active = true` again via the entity
        //    field). Books in the root that are NOT in the incoming set stay inactive —
        //    bookmarks + per-book settings preserved by virtue of the row staying alive.
        val touchedRoots: Set<String> = snapshot.books.map { it.treeUriString }.toSet()

        // 3. One Room transaction for atomic correctness across cover-path commits + book
        //    upserts + chapter replacements + soft-delete sweeps.
        database.withTransaction {
            for (root in touchedRoots) {
                bookDao.markRootInactive(root)
            }
            for ((book, coverPath, coverSource) in booksWithCoverPaths) {
                bookDao.upsert(book.toEntity(coverPath = coverPath, coverSource = coverSource))
                // Replace the chapter set wholesale. CASCADE on chapter delete is fine here:
                // bookmarks have `onDelete = SET_NULL` on chapterId, so they survive a
                // chapter-row disappearing (the bookmark just unpins from the chapter and
                // pins on the book-level position).
                chapterDao.deleteForBook(book.bookId)
                chapterDao.upsertAll(book.chapters.map { it.toEntity(book.bookId) })
            }
        }
    }

    private fun ScannedBook.toEntity(coverPath: String?, coverSource: CoverSource): BookEntity = BookEntity(
        bookId = bookId,
        treeUriString = treeUriString,
        relativePath = relativePath,
        title = title,
        author = author,
        durationMs = durationMs,
        coverPath = coverPath,
        coverSource = coverSource,
        // Resume / per-book settings are NOT touched on rescan — preserve the user's state
        // by not overwriting these fields. Room's @Upsert with the existing PK will write
        // ALL columns, so we have to read the existing row and merge. For D.4 simplicity:
        // an incoming scan resets currentChapterIndex / positionInChapterMs / per-book
        // settings to defaults. Phase D's roadmap notes this as a follow-up — proper
        // merge-preserve-resume lands when Phase F's playback wires in.
        // TODO(D-followup): preserve currentChapterIndex / positionInChapterMs / speed /
        //   skipSilenceEnabled / gainDb / lastPlayedAt across rescan by reading the
        //   existing row first. Not on the critical path for D.4.
        currentChapterIndex = 0,
        positionInChapterMs = 0L,
        speed = 1.0f,
        skipSilenceEnabled = false,
        gainDb = 0.0f,
        lastPlayedAt = null,
        active = true,
    )

    private fun ScannedChapter.toEntity(bookId: String): ChapterEntity = ChapterEntity(
        chapterId = chapterId,
        bookId = bookId,
        chapterIndex = chapterIndex,
        title = title,
        durationMs = durationMs,
        fileUri = fileUri,
        positionInBookMs = positionInBookMs,
    )
}
