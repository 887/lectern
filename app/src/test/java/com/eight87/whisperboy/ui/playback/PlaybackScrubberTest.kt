package com.eight87.whisperboy.ui.playback

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eight87.whisperboy.ui.settings.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose coverage for [PlayerScrubber] (private→internal in the same
 * commit that lands this test). The composable wraps a Material3 [Slider] with
 * formatted position/duration labels, clamps position into `[0, duration]` and
 * disables the slider when `chapterDurationMs <= 0`.
 *
 * Drag fidelity is asserted via the Slider's semantics `SetProgress` action — we
 * invoke it and pin that `onSeek` fires with the requested value. The pointer-input
 * path is exercised by Compose's own slider tests; we don't re-test gesture
 * arithmetic here.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class PlaybackScrubberTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders position and duration labels in mm-ss`() {
        composeRule.setContent {
            PlayerScrubber(positionInChapterMs = 65_000L, chapterDurationMs = 180_000L, onSeek = {})
        }
        // 65_000ms = 1:05, 180_000ms = 3:00
        composeRule.onNodeWithText("1:05").assertExists()
        composeRule.onNodeWithText("3:00").assertExists()
    }

    @Test
    fun `slider semantics carry the position value within range`() {
        composeRule.setContent {
            PlayerScrubber(positionInChapterMs = 30_000L, chapterDurationMs = 120_000L, onSeek = {})
        }
        val node = composeRule.onNodeWithTag("player_scrubber_slider").fetchSemanticsNode()
        val range = node.config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(30_000f, range.current, 0.5f)
        assertEquals(0f, range.range.start, 0.5f)
        assertEquals(120_000f, range.range.endInclusive, 0.5f)
    }

    @Test
    fun `SetProgress action fires onSeek with the requested value`() {
        var lastSeek = -1L
        composeRule.setContent {
            PlayerScrubber(
                positionInChapterMs = 0L,
                chapterDurationMs = 100_000L,
                onSeek = { lastSeek = it },
            )
        }
        val node = composeRule.onNodeWithTag("player_scrubber_slider").fetchSemanticsNode()
        val setProgress = node.config[SemanticsActions.SetProgress]
        // Ask for halfway. `onSeek` should receive ~50_000ms (slider precision).
        val ok = setProgress.action!!.invoke(50_000f)
        assertTrue(ok)
        // 5% tolerance — Slider rounds to its internal step.
        val tolerance = 100_000L * 5 / 100
        assertTrue(
            "expected ~50_000, got $lastSeek (tolerance ±$tolerance)",
            kotlin.math.abs(lastSeek - 50_000L) <= tolerance,
        )
    }

    @Test
    fun `slider is disabled when duration is zero`() {
        composeRule.setContent {
            PlayerScrubber(positionInChapterMs = 0L, chapterDurationMs = 0L, onSeek = {})
        }
        composeRule.onNodeWithTag("player_scrubber_slider").assertIsNotEnabled()
    }

    @Test
    fun `slider is enabled for a positive duration`() {
        composeRule.setContent {
            PlayerScrubber(positionInChapterMs = 10L, chapterDurationMs = 1_000L, onSeek = {})
        }
        composeRule.onNodeWithTag("player_scrubber_slider").assertIsEnabled()
    }

    @Test
    fun `position above duration is clamped to duration in semantics`() {
        composeRule.setContent {
            PlayerScrubber(
                positionInChapterMs = 999_999L,
                chapterDurationMs = 60_000L,
                onSeek = {},
            )
        }
        // Position should clamp at 60_000. Both the position label and duration label
        // will render "1:00" (clamped position == duration), so we assert on semantics
        // rather than text — the slider's ProgressBarRangeInfo is unambiguous.
        val node = composeRule.onNodeWithTag("player_scrubber_slider").fetchSemanticsNode()
        val range = node.config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(60_000f, range.current, 0.5f)
    }

    @Test
    fun `negative position is clamped to zero`() {
        composeRule.setContent {
            PlayerScrubber(
                positionInChapterMs = -5_000L,
                chapterDurationMs = 60_000L,
                onSeek = {},
            )
        }
        val node = composeRule.onNodeWithTag("player_scrubber_slider").fetchSemanticsNode()
        val range = node.config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0f, range.current, 0.5f)
        composeRule.onNodeWithText("0:00").assertExists()
    }
}
