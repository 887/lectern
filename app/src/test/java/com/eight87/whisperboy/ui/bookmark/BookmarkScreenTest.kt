package com.eight87.whisperboy.ui.bookmark

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.eight87.whisperboy.data.library.BookmarkEntity
import com.eight87.whisperboy.data.library.BookmarkSource
import com.eight87.whisperboy.data.library.ChapterEntity
import com.eight87.whisperboy.data.library.ChapterSource
import com.eight87.whisperboy.ui.settings.TestApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase H.1 — Robolectric Compose coverage for [BookmarkScreen].
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class BookmarkScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ch1 = ChapterEntity(
        chapterId = "c1",
        bookId = "b1",
        chapterIndex = 0,
        title = "Chapter One",
        durationMs = 600_000L,
        positionInBookMs = 0L,
    )
    private val ch2 = ChapterEntity(
        chapterId = "c2",
        bookId = "b1",
        chapterIndex = 1,
        title = "Chapter Two",
        durationMs = 600_000L,
        positionInBookMs = 600_000L,
    )

    private val bm1 = BookmarkEntity(
        bookmarkId = "bm1",
        bookId = "b1",
        chapterId = "c1",
        title = "Mark Alpha",
        positionInBookMs = 5_000L,
        addedAt = System.currentTimeMillis() - 5_000L,
    )
    private val bm2 = BookmarkEntity(
        bookmarkId = "bm2",
        bookId = "b1",
        chapterId = "c2",
        title = "Mark Beta",
        positionInBookMs = 610_000L,
        addedAt = System.currentTimeMillis() - 5_000L,
    )

    private class FakeBookmarkSource(
        private val flow: Flow<List<BookmarkEntity>>,
    ) : BookmarkSource {
        val deleted = mutableListOf<String>()
        val renamed = mutableListOf<Pair<String, String?>>()
        override fun observeBookmarksForBook(bookId: String) = flow
        override suspend fun addBookmark(
            bookId: String,
            chapterId: String?,
            title: String?,
            positionInBookMs: Long,
            setBySleepTimer: Boolean,
        ) = Unit
        override suspend fun renameBookmark(id: String, title: String?) {
            renamed += id to title
        }
        override suspend fun deleteBookmark(id: String) {
            deleted += id
        }
    }

    private class FakeChapterSource(
        private val flow: Flow<List<ChapterEntity>>,
    ) : ChapterSource {
        override fun observeChaptersForBook(bookId: String) = flow
        override suspend fun chaptersFor(bookId: String): List<ChapterEntity> = emptyList()
    }

    @Test
    fun `renders bookmark groups per chapter`() {
        val bookmarksFlow = MutableStateFlow(listOf(bm1, bm2))
        val chaptersFlow = MutableStateFlow(listOf(ch1, ch2))
        composeRule.setContent {
            BookmarkScreen(
                bookId = "b1",
                bookmarkSource = FakeBookmarkSource(bookmarksFlow),
                chapterSource = FakeChapterSource(chaptersFlow),
                onBack = {},
                onBookmarkSeek = {},
            )
        }
        // Chapter headers render as group dividers.
        composeRule.onNodeWithText("Chapter One").assertExists()
        composeRule.onNodeWithText("Chapter Two").assertExists()
        // Bookmark rows render their titles.
        composeRule.onNodeWithText("Mark Alpha").assertExists()
        composeRule.onNodeWithText("Mark Beta").assertExists()
    }

    @Test
    fun `tapping a bookmark fires onBookmarkSeek with the bookmark position`() {
        val bookmarksFlow = MutableStateFlow(listOf(bm1))
        val chaptersFlow = MutableStateFlow(listOf(ch1))
        val seekHistory = mutableListOf<Long>()
        composeRule.setContent {
            BookmarkScreen(
                bookId = "b1",
                bookmarkSource = FakeBookmarkSource(bookmarksFlow),
                chapterSource = FakeChapterSource(chaptersFlow),
                onBack = {},
                onBookmarkSeek = { seekHistory += it },
            )
        }
        composeRule.onNodeWithText("Mark Alpha").performClick()
        assertEquals(listOf(5_000L), seekHistory)
    }

    @Test
    fun `long-press on a bookmark opens the action sheet`() {
        val bookmarksFlow = MutableStateFlow(listOf(bm1))
        val chaptersFlow = MutableStateFlow(listOf(ch1))
        composeRule.setContent {
            BookmarkScreen(
                bookId = "b1",
                bookmarkSource = FakeBookmarkSource(bookmarksFlow),
                chapterSource = FakeChapterSource(chaptersFlow),
                onBack = {},
                onBookmarkSeek = {},
            )
        }
        composeRule.onNodeWithText("Mark Alpha").performTouchInput { longClick() }
        composeRule.waitForIdle()
        // Action sheet shows Rename + Delete affordances.
        assertTrue(
            "Rename action should be present after long-press",
            composeRule.onAllNodesWithText("Rename").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "Delete action should be present after long-press",
            composeRule.onAllNodesWithText("Delete").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun `empty state renders when no bookmarks exist`() {
        val bookmarksFlow = MutableStateFlow<List<BookmarkEntity>>(emptyList())
        val chaptersFlow = MutableStateFlow(listOf(ch1))
        composeRule.setContent {
            BookmarkScreen(
                bookId = "b1",
                bookmarkSource = FakeBookmarkSource(bookmarksFlow),
                chapterSource = FakeChapterSource(chaptersFlow),
                onBack = {},
                onBookmarkSeek = {},
            )
        }
        composeRule.onNodeWithText(
            "No bookmarks yet. Tap the bookmark button in the player to add one.",
        ).assertExists()
    }
}
