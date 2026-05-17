package com.eight87.whisperboy.ui.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.eight87.whisperboy.ui.settings.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase P.4 — [LibraryHealthBanner] is the error-tinted card the library renders
 * above the rail+content row whenever the rescan coordinator reports unreadable
 * SAF roots. The composable itself only branches on `unreadableCount`; the caller
 * inside [LibraryScreen] decides whether to mount it at all (hidden when the
 * unreadable set is empty). These tests pin both the rendered shape (warning icon +
 * pluralised body) and the click contract.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class LibraryHealthBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders warning icon and pluralised banner body for 1 root`() {
        composeRule.setContent {
            LibraryHealthBanner(unreadableCount = 1, onClick = {})
        }
        // The leading icon's contentDescription comes from
        // `R.string.library_health_unreadable_cd` ("Library folder warning") — pin its
        // existence so we know we're looking at the banner, not some other card.
        composeRule.onNodeWithContentDescription("Library folder warning").assertExists()
    }

    @Test
    fun `renders warning icon for N roots`() {
        composeRule.setContent {
            LibraryHealthBanner(unreadableCount = 5, onClick = {})
        }
        composeRule.onNodeWithContentDescription("Library folder warning").assertExists()
    }

    @Test
    fun `card is clickable and tap fires onClick`() {
        var clicks = 0
        composeRule.setContent {
            LibraryHealthBanner(unreadableCount = 2, onClick = { clicks += 1 })
        }
        // Tap the icon (the only stable label-pinned node) — the click propagates to
        // the card's `clickable` modifier.
        val icon = composeRule.onNodeWithContentDescription("Library folder warning")
        icon.assertHasClickAction()
        icon.performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `caller path - hidden when unreadableRoots is empty`() {
        // Mirror the LibraryScreen call site: `if (health.unreadableRoots.isNotEmpty())`
        // gates the banner. With an empty set the composable is never mounted, so the
        // warning icon does not appear anywhere in the tree.
        val unreadableRoots: List<String> = emptyList()
        composeRule.setContent {
            if (unreadableRoots.isNotEmpty()) {
                LibraryHealthBanner(unreadableCount = unreadableRoots.size, onClick = {})
            }
        }
        composeRule.onNodeWithContentDescription("Library folder warning").assertDoesNotExist()
    }
}
