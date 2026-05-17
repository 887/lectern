package com.eight87.whisperboy.ui.playback

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.eight87.whisperboy.ui.settings.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase F.1 — Robolectric Compose coverage for the player's 5-button transport row
 * (`PlayerTransport` inside [PlaybackScreen]). The composable is exposed as `internal`
 * to keep this test surface tight; production callers continue to be the single
 * `PlayerTransport(...)` call inside `PlayerLoadedContent`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class PlaybackTransportTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Calls(
        var playPause: Int = 0,
        var rewind: Int = 0,
        var forward: Int = 0,
        var prev: Int = 0,
        var next: Int = 0,
    )

    @Test
    fun `renders all five transport buttons`() {
        composeRule.setContent {
            PlayerTransport(
                isPlaying = false,
                rewindSeconds = 30,
                forwardSeconds = 30,
                onPlayPause = {},
                onRewind = {},
                onForward = {},
                onPrev = {},
                onNext = {},
                onSetRewindSeconds = {},
                onSetForwardSeconds = {},
            )
        }
        composeRule.onNodeWithContentDescription("Previous chapter").assertExists()
        composeRule.onNodeWithContentDescription("Rewind 30 seconds").assertExists()
        composeRule.onNodeWithContentDescription("Play").assertExists()
        composeRule.onNodeWithContentDescription("Forward 30 seconds").assertExists()
        composeRule.onNodeWithContentDescription("Next chapter").assertExists()
    }

    @Test
    fun `center button reads Pause when isPlaying is true`() {
        composeRule.setContent {
            PlayerTransport(
                isPlaying = true,
                rewindSeconds = 30,
                forwardSeconds = 30,
                onPlayPause = {},
                onRewind = {},
                onForward = {},
                onPrev = {},
                onNext = {},
                onSetRewindSeconds = {},
                onSetForwardSeconds = {},
            )
        }
        composeRule.onNodeWithContentDescription("Pause").assertExists()
    }

    @Test
    fun `each tap dispatches to the right callback`() {
        val calls = Calls()
        composeRule.setContent {
            PlayerTransport(
                isPlaying = false,
                rewindSeconds = 10,
                forwardSeconds = 60,
                onPlayPause = { calls.playPause++ },
                onRewind = { calls.rewind++ },
                onForward = { calls.forward++ },
                onPrev = { calls.prev++ },
                onNext = { calls.next++ },
                onSetRewindSeconds = {},
                onSetForwardSeconds = {},
            )
        }
        composeRule.onNodeWithContentDescription("Previous chapter").performClick()
        composeRule.onNodeWithContentDescription("Rewind 10 seconds").performClick()
        composeRule.onNodeWithContentDescription("Play").performClick()
        composeRule.onNodeWithContentDescription("Forward 60 seconds").performClick()
        composeRule.onNodeWithContentDescription("Next chapter").performClick()

        assertEquals(1, calls.prev)
        assertEquals(1, calls.rewind)
        assertEquals(1, calls.playPause)
        assertEquals(1, calls.forward)
        assertEquals(1, calls.next)
    }
}
