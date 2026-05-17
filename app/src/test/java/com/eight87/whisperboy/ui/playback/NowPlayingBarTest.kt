package com.eight87.whisperboy.ui.playback

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.eight87.whisperboy.data.library.BookEntity
import com.eight87.whisperboy.data.library.ChapterEntity
import com.eight87.whisperboy.playback.NowPlayingState
import com.eight87.whisperboy.playback.PlaybackUiState
import com.eight87.whisperboy.playback.TransportCommands
import com.eight87.whisperboy.ui.library.NowPlayingBar
import com.eight87.whisperboy.ui.settings.TestApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase E.6 — Robolectric Compose coverage for the mini [NowPlayingBar]:
 *  - 5 transport buttons render
 *  - title + chapter render in the info row
 *  - Play / Pause label flips on `isPlaying`
 *  - rewind button taps dispatch through [TransportCommands.rewind]
 *
 * The transport calls are suspend functions; the test waits for the Compose
 * idle barrier (which drains the `rememberCoroutineScope` continuation)
 * before asserting.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class NowPlayingBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val book = BookEntity(
        bookId = "b1",
        treeUriString = "content://example/tree",
        relativePath = "book/",
        title = "Moby Dick",
        author = "Herman Melville",
        durationMs = 3_600_000L,
        coverPath = null,
    )

    private val chapter = ChapterEntity(
        chapterId = "c0",
        bookId = "b1",
        chapterIndex = 0,
        title = "Loomings",
        durationMs = 600_000L,
        positionInBookMs = 0L,
    )

    private class FakeNowPlayingState(initial: PlaybackUiState) : NowPlayingState {
        override val state: StateFlow<PlaybackUiState> = MutableStateFlow(initial)
    }

    private class RecordingTransport : TransportCommands {
        var plays = 0
        var pauses = 0
        var rewinds = 0
        var forwards = 0
        var prevs = 0
        var nexts = 0
        override suspend fun play() {
            plays++
        }
        override suspend fun pause() {
            pauses++
        }
        override suspend fun seekTo(positionInBookMs: Long) = Unit
        override suspend fun rewind() {
            rewinds++
        }
        override suspend fun forward() {
            forwards++
        }
        override suspend fun nextChapter() {
            nexts++
        }
        override suspend fun prevChapter() {
            prevs++
        }
        override suspend fun setSpeed(speed: Float) = Unit
        override suspend fun setSkipSilence(enabled: Boolean) = Unit
        override suspend fun setGain(gainDb: Float) = Unit
    }

    private fun loaded(isPlaying: Boolean) = PlaybackUiState.Loaded(
        book = book,
        currentChapter = chapter,
        positionInBookMs = 30_000L,
        isPlaying = isPlaying,
        speed = 1.0f,
        skipSilenceEnabled = false,
        gainDb = 0.0f,
    )

    @Test
    fun `renders all five transport buttons`() {
        composeRule.setContent {
            NowPlayingBar(
                nowPlayingState = FakeNowPlayingState(loaded(isPlaying = false)),
                transport = RecordingTransport(),
                onExpand = {},
            )
        }
        composeRule.onNodeWithContentDescription("Rewind 0 seconds").assertExists()
        composeRule.onNodeWithContentDescription("Previous chapter").assertExists()
        composeRule.onNodeWithContentDescription("Play").assertExists()
        composeRule.onNodeWithContentDescription("Next chapter").assertExists()
        composeRule.onNodeWithContentDescription("Forward 0 seconds").assertExists()
    }

    @Test
    fun `renders book title and current chapter title`() {
        composeRule.setContent {
            NowPlayingBar(
                nowPlayingState = FakeNowPlayingState(loaded(isPlaying = false)),
                transport = RecordingTransport(),
                onExpand = {},
            )
        }
        composeRule.onNodeWithText("Moby Dick").assertExists()
        composeRule.onNodeWithText("Loomings").assertExists()
    }

    @Test
    fun `play button flips to Pause label when isPlaying is true`() {
        composeRule.setContent {
            NowPlayingBar(
                nowPlayingState = FakeNowPlayingState(loaded(isPlaying = true)),
                transport = RecordingTransport(),
                onExpand = {},
            )
        }
        composeRule.onNodeWithContentDescription("Pause").assertExists()
    }

    @Test
    fun `tapping rewind dispatches to TransportCommands rewind`() {
        val transport = RecordingTransport()
        composeRule.setContent {
            NowPlayingBar(
                nowPlayingState = FakeNowPlayingState(loaded(isPlaying = false)),
                transport = transport,
                onExpand = {},
            )
        }
        composeRule.onNodeWithContentDescription("Rewind 0 seconds").performClick()
        composeRule.waitForIdle()
        assertEquals(1, transport.rewinds)
    }
}
