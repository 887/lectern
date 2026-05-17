package com.eight87.whisperboy.ui.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.eight87.whisperboy.ui.settings.TestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase E + m3-expressive D.1 — [LibraryEmptyState] renders the welcome callout when
 * the library has no books to show. The composable itself takes no callbacks (it is
 * pure copy on `surfaceContainerHigh`); the surrounding [LibraryScreen] decides when
 * to mount it. These tests pin the headline + body strings.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class LibraryEmptyStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders headline copy`() {
        composeRule.setContent { LibraryEmptyState() }
        // Pinned against the canonical English string. If translations.md adds a new
        // locale the tag system would change; the EN canonical lives in values/strings.xml
        // and the test runs under the default Locale.
        composeRule.onNodeWithText("No books yet").assertExists()
    }

    @Test
    fun `renders body subtitle copy`() {
        composeRule.setContent { LibraryEmptyState() }
        composeRule.onNodeWithText(
            "whisperboy is scanning your folders. Books will appear here as they're found.",
        ).assertExists()
    }
}
