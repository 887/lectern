package com.eight87.whisperboy.ui.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.eight87.whisperboy.data.library.LibraryHealth
import com.eight87.whisperboy.data.library.LibraryRescanCoordinator
import com.eight87.whisperboy.data.library.RescanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase K.1 — Robolectric Compose coverage for [SettingsScreen]. Uses a fake
 * [LibraryRescanCoordinator] to capture `requestRescan` invocations from the
 * "Rescan now" button.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeRescan : LibraryRescanCoordinator {
        override val state: StateFlow<RescanState> = MutableStateFlow(RescanState.Idle)
        override val health: StateFlow<LibraryHealth> = MutableStateFlow(LibraryHealth())
        val calls = mutableListOf<Boolean>()
        override fun requestRescan(force: Boolean) {
            calls += force
        }
    }

    @Test
    fun `renders all five category rows`() {
        composeRule.setContent {
            SettingsScreen(
                libraryRescanCoordinator = FakeRescan(),
                onBack = {},
                onAboutClick = {},
                onLibraryFoldersClick = {},
                onPlaybackClick = {},
                onSleepTimerClick = {},
                onThemeClick = {},
            )
        }
        composeRule.onNodeWithText("Playback").assertExists()
        composeRule.onNodeWithText("Sleep timer").assertExists()
        composeRule.onNodeWithText("Library").assertExists()
        composeRule.onNodeWithText("Theme").assertExists()
        composeRule.onNodeWithText("About").assertExists()
        composeRule.onNodeWithText("Rescan now").assertExists()
    }

    @Test
    fun `tapping each row fires the corresponding callback`() {
        var back = 0
        var about = 0
        var library = 0
        var playback = 0
        var sleep = 0
        var theme = 0
        composeRule.setContent {
            SettingsScreen(
                libraryRescanCoordinator = FakeRescan(),
                onBack = { back++ },
                onAboutClick = { about++ },
                onLibraryFoldersClick = { library++ },
                onPlaybackClick = { playback++ },
                onSleepTimerClick = { sleep++ },
                onThemeClick = { theme++ },
            )
        }
        composeRule.onNodeWithText("Playback").performClick()
        composeRule.onNodeWithText("Sleep timer").performClick()
        composeRule.onNodeWithText("Library").performClick()
        composeRule.onNodeWithText("Theme").performClick()
        // About lives in the second card below "Rescan now" — scroll the verticalScroll
        // column to bring it into the viewport before clicking.
        composeRule.onNodeWithText("About").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, playback)
        assertEquals(1, sleep)
        assertEquals(1, library)
        assertEquals(1, theme)
        assertEquals(1, about)
        assertEquals(1, back)
    }

    @Test
    fun `Rescan now button fires requestRescan with force=true`() {
        val rescan = FakeRescan()
        composeRule.setContent {
            SettingsScreen(
                libraryRescanCoordinator = rescan,
                onBack = {},
                onAboutClick = {},
                onLibraryFoldersClick = {},
                onPlaybackClick = {},
                onSleepTimerClick = {},
                onThemeClick = {},
            )
        }
        composeRule.onNodeWithText("Rescan now").performClick()
        assertEquals(listOf(true), rescan.calls)
        assertTrue(rescan.calls.isNotEmpty())
        assertFalse(rescan.calls.first() == false)
    }
}
