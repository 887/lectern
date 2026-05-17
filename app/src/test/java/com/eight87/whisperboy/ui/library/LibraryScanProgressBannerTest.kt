package com.eight87.whisperboy.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.eight87.whisperboy.data.library.RescanState
import com.eight87.whisperboy.data.library.ScanPhase
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
    fun `renders Discovering phase with cumulative counts`() {
        composeRule.setContent {
            LibraryScanProgressBanner(
                running = RescanState.Running(
                    booksFound = 3,
                    chaptersFound = 12,
                    phase = ScanPhase.Discovering,
                ),
            )
        }
        composeRule.onNodeWithContentDescription("Library scan progress").assertExists()
        composeRule.onNodeWithText("Discovering — 3 books, 12 chapters").assertExists()
    }

    @Test
    fun `renders Discovering with zero counts at the very start of a scan`() {
        composeRule.setContent {
            LibraryScanProgressBanner(running = RescanState.Running())
        }
        composeRule.onNodeWithText("Discovering — 0 books, 0 chapters").assertExists()
    }

    @Test
    fun `renders Analyzing phase as analyzed-over-total when totals are known`() {
        composeRule.setContent {
            LibraryScanProgressBanner(
                running = RescanState.Running(
                    booksFound = 5,
                    chaptersFound = 100,
                    phase = ScanPhase.Analyzing,
                    analyzedChapters = 42,
                    totalChapters = 100,
                ),
            )
        }
        composeRule.onNodeWithText("Analyzing — 42 / 100 chapters").assertExists()
    }

    @Test
    fun `renders Writing phase with the updating-library string`() {
        composeRule.setContent {
            LibraryScanProgressBanner(
                running = RescanState.Running(phase = ScanPhase.Writing),
            )
        }
        composeRule.onNodeWithText("Updating library…").assertExists()
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
