package com.eight87.whisperboy.ui.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.eight87.whisperboy.ui.settings.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose coverage for the library top-bar's search input
 * ([LibrarySearchBar]). The composable is `internal` so the test can mount it
 * standalone; production callers continue to invoke it from
 * [LibraryScreen]'s search-mode branch.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class LibrarySearchBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders placeholder text`() {
        composeRule.setContent {
            LibrarySearchBar(
                query = "",
                onQueryChange = {},
                onClose = {},
            )
        }
        composeRule.onNodeWithText("Search title or author").assertExists()
    }

    @Test
    fun `typing into the field fires onQueryChange`() {
        val captured = mutableListOf<String>()
        composeRule.setContent {
            LibrarySearchBar(
                query = "",
                onQueryChange = { captured += it },
                onClose = {},
            )
        }
        composeRule.onNodeWithText("Search title or author").performTextInput("foo")
        // One character at a time produces multiple emissions; the final value
        // contains every char we typed.
        assert(captured.isNotEmpty()) { "expected at least one onQueryChange emission" }
        assert(captured.last().contains("o")) { "expected typed char to reach callback" }
    }

    @Test
    fun `close icon button fires onClose`() {
        var closes = 0
        composeRule.setContent {
            LibrarySearchBar(
                query = "abc",
                onQueryChange = {},
                onClose = { closes++ },
            )
        }
        composeRule.onNodeWithContentDescription("Close search").performClick()
        assertEquals(1, closes)
    }
}
