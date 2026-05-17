package com.eight87.whisperboy.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.eight87.whisperboy.data.library.RescanState
import com.eight87.whisperboy.ui.settings.TestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue-3 — [LibraryScanProgressBanner] visibility + content tests. The composable itself
 * renders unconditionally when called; the LibraryScreen call-site decides whether to
 * mount it. These tests pin both the rendered shape (counts string + accessibility
 * label) and the call-site contract (hidden on Idle, mounted on Running, dismissed on
 * Running→Idle).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class LibraryScanProgressBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders content description + counts string for Running`() {
        composeRule.setContent {
            LibraryScanProgressBanner(running = RescanState.Running(booksFound = 3, chaptersFound = 12))
        }
        composeRule.onNodeWithContentDescription("Library scan progress").assertExists()
        // The English string includes "3 books, 12 chapters" — assert substring presence.
        composeRule.onNodeWithText("Scanning library — 3 books, 12 chapters").assertExists()
    }

    @Test
    fun `renders with zero counts during structural pass`() {
        composeRule.setContent {
            LibraryScanProgressBanner(running = RescanState.Running())
        }
        composeRule.onNodeWithText("Scanning library — 0 books, 0 chapters").assertExists()
    }

    @Test
    fun `caller path - hidden on Idle, mounted on Running, dismissed on Idle again`() {
        var state by mutableStateOf<RescanState>(RescanState.Idle)
        composeRule.setContent {
            val s = state
            if (s is RescanState.Running) {
                LibraryScanProgressBanner(running = s)
            }
        }
        composeRule.onNodeWithContentDescription("Library scan progress").assertDoesNotExist()

        composeRule.runOnIdle { state = RescanState.Running(booksFound = 1, chaptersFound = 4) }
        composeRule.onNodeWithContentDescription("Library scan progress").assertExists()

        composeRule.runOnIdle { state = RescanState.Idle }
        composeRule.onNodeWithContentDescription("Library scan progress").assertDoesNotExist()
    }
}
