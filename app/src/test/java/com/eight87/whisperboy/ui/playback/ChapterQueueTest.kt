package com.eight87.whisperboy.ui.playback

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.eight87.whisperboy.data.library.ChapterEntity
import com.eight87.whisperboy.data.library.ChapterSource
import com.eight87.whisperboy.ui.settings.TestApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase F.5 — Robolectric Compose coverage for the inline [ChapterQueue]
 * rendered below the player transport row. The composable is `internal` so
 * the test can mount it directly against a fake [ChapterSource]; production
 * callers continue to be `PlayerLoadedContent` inside [PlaybackScreen].
 *
 * `chapter_row` / `chapter_row_active` testTags were added so the active-row
 * treatment is observable without scraping colour tokens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class ChapterQueueTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val chapters = listOf(
        ChapterEntity(
            chapterId = "c0",
            bookId = "b1",
            chapterIndex = 0,
            title = "Prologue",
            durationMs = 120_000L,
            positionInBookMs = 0L,
        ),
        ChapterEntity(
            chapterId = "c1",
            bookId = "b1",
            chapterIndex = 1,
            title = "Chapter One",
            durationMs = 300_000L,
            positionInBookMs = 120_000L,
        ),
        ChapterEntity(
            chapterId = "c2",
            bookId = "b1",
            chapterIndex = 2,
            title = "Chapter Two",
            durationMs = 400_000L,
            positionInBookMs = 420_000L,
        ),
    )

    private class FakeChapterSource(
        private val flow: Flow<List<ChapterEntity>>,
    ) : ChapterSource {
        override fun observeChaptersForBook(bookId: String) = flow
        override suspend fun chaptersFor(bookId: String): List<ChapterEntity> = emptyList()
    }

    @Test
    fun `renders one row per chapter`() {
        val flow = MutableStateFlow(chapters)
        composeRule.setContent {
            ChapterQueue(
                bookId = "b1",
                currentChapterIndex = 1,
                chapterSource = FakeChapterSource(flow),
                listState = null,
                onChapterTap = {},
            )
        }
        composeRule.onNodeWithText("Prologue").assertExists()
        composeRule.onNodeWithText("Chapter One").assertExists()
        composeRule.onNodeWithText("Chapter Two").assertExists()
    }

    @Test
    fun `active chapter row has the active testTag exactly once`() {
        val flow = MutableStateFlow(chapters)
        composeRule.setContent {
            ChapterQueue(
                bookId = "b1",
                currentChapterIndex = 1,
                chapterSource = FakeChapterSource(flow),
                listState = null,
                onChapterTap = {},
            )
        }
        composeRule.waitForIdle()
        // Exactly one active row, two inactive ones.
        assertEquals(
            1,
            composeRule.onAllNodesWithTag("chapter_row_active", useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `tap on a row fires onChapterTap with the position of that chapter`() {
        val flow = MutableStateFlow(chapters)
        val taps = mutableListOf<Long>()
        composeRule.setContent {
            ChapterQueue(
                bookId = "b1",
                currentChapterIndex = 0,
                chapterSource = FakeChapterSource(flow),
                listState = null,
                onChapterTap = { taps += it },
            )
        }
        composeRule.onNodeWithText("Chapter Two").performClick()
        assertEquals(listOf(420_000L), taps)
    }
}
