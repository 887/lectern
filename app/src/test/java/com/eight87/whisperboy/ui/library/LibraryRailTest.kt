package com.eight87.whisperboy.ui.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.eight87.whisperboy.data.library.BookFilter
import com.eight87.whisperboy.ui.settings.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase E.2 — Robolectric Compose coverage for the hand-rolled vertical [LibraryRail].
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class LibraryRailTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders one label per filter tab`() {
        composeRule.setContent {
            LibraryRail(filter = BookFilter.All, onFilterChange = {})
        }
        composeRule.onNodeWithText("All").assertExists()
        composeRule.onNodeWithText("Current").assertExists()
        composeRule.onNodeWithText("Not started").assertExists()
        composeRule.onNodeWithText("Completed").assertExists()
        // One Box per BookFilter entry — assert via the per-tab testTag pattern.
        BookFilter.entries.forEach { option ->
            composeRule
                .onNodeWithTag("rail_filter_${option.name.lowercase()}")
                .assertExists()
        }
    }

    @Test
    fun `tap on a tab fires onFilterChange with the correct BookFilter`() {
        var lastFilter: BookFilter? = null
        composeRule.setContent {
            LibraryRail(filter = BookFilter.All, onFilterChange = { lastFilter = it })
        }
        composeRule.onNodeWithTag("rail_filter_completed").performClick()
        assertEquals(BookFilter.Completed, lastFilter)

        composeRule.onNodeWithTag("rail_filter_current").performClick()
        assertEquals(BookFilter.Current, lastFilter)

        composeRule.onNodeWithTag("rail_filter_${BookFilter.NotStarted.name.lowercase()}")
            .performClick()
        assertEquals(BookFilter.NotStarted, lastFilter)
    }

    @Test
    fun `accent stripe is present exactly once for the active tab`() {
        composeRule.setContent {
            LibraryRail(filter = BookFilter.NotStarted, onFilterChange = {})
        }
        // The accent stripe is rendered inside the selected tab — exactly one node.
        composeRule.onAllNodesWithTag("rail_accent", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `accent stripe absent on tabs that are not selected`() {
        composeRule.setContent {
            LibraryRail(filter = BookFilter.All, onFilterChange = {})
        }
        // All filter tab boxes exist; only the active tab has the accent.
        composeRule.onAllNodesWithTag("rail_accent", useUnmergedTree = true).assertCountEquals(1)
        // Sanity — the rail itself is present.
        composeRule.onNodeWithTag("library_rail").assertExists()
    }
}
