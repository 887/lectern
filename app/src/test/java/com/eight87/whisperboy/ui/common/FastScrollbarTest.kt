package com.eight87.whisperboy.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.eight87.whisperboy.ui.settings.TestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose coverage for [FastScrollbar]. The thumb only renders when the
 * underlying [LazyListState] reports the surface is scrollable — short lists yield no
 * scrollbar at all (composable returns early). Section chips render at fractional Y
 * positions when [sectionStarts] is non-empty, and each chip carries a per-label
 * testTag `fast_scrollbar_section_<label>` so we can pin the count.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class FastScrollbarTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Helper: render a scrollable list with FastScrollbar overlaying it. */
    private fun mountList(
        itemCount: Int,
        sections: List<Pair<Int, String>>? = null,
        listHeightDp: Int = 200,
    ) {
        composeRule.setContent {
            val state = rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = state,
                    modifier = Modifier.height(listHeightDp.dp).fillMaxSize(),
                ) {
                    items((0 until itemCount).toList()) { i ->
                        Text(text = "Item $i", modifier = Modifier.height(40.dp))
                    }
                }
                FastScrollbar(state = state, sectionStarts = sections)
            }
        }
    }

    @Test
    fun `renders thumb when content overflows viewport`() {
        // 50 items × 40dp ≈ 2000dp content, viewport 200dp → scrollable → thumb shows.
        mountList(itemCount = 50)
        composeRule.onNodeWithTag("fast_scrollbar").assertExists()
        composeRule.onNodeWithTag("fast_scrollbar_thumb").assertExists()
    }

    @Test
    fun `hides scrollbar when list is empty`() {
        mountList(itemCount = 0)
        // totalItems == 0 → early return → no testTags exist anywhere.
        composeRule.onNodeWithTag("fast_scrollbar").assertDoesNotExist()
        composeRule.onNodeWithTag("fast_scrollbar_thumb").assertDoesNotExist()
    }

    @Test
    fun `hides scrollbar when content fits in viewport (not scrollable)`() {
        // 2 items × 40dp == 80dp, viewport 200dp → canScroll false → no thumb.
        mountList(itemCount = 2)
        composeRule.onNodeWithTag("fast_scrollbar_thumb").assertDoesNotExist()
    }

    @Test
    fun `does not render section chips when sectionStarts is empty`() {
        mountList(itemCount = 50, sections = emptyList())
        // No labels supplied → the chips block is skipped wholesale.
        composeRule.onNodeWithTag("fast_scrollbar_section_A").assertDoesNotExist()
    }

    @Test
    fun `does not render section chips when sectionStarts is null`() {
        // sections == null is the default — same outcome.
        mountList(itemCount = 50, sections = null)
        composeRule.onNodeWithTag("fast_scrollbar_section_A").assertDoesNotExist()
    }
}
