package com.eight87.whisperboy.ui.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose coverage for [ColorPickerDialog] — the K.5
 * follow-up colour picker ported from tonearmboy.
 *
 * Covers:
 *  - dialog mounts and shows the SV square / hue slider / preview;
 *  - confirm fires `onConfirm` with the seed (no edits → same RGB back);
 *  - cancel fires `onDismiss`;
 *  - the Reset button is present when `onReset` is non-null and fires
 *    its callback;
 *  - the Reset button is HIDDEN when `onReset` is null (the "no unset
 *    state" call shape).
 *
 * The HSV maths is exercised directly through the [rgbToHsv] /
 * [colorToRgbLong] internal helpers — they round-trip without
 * needing a Compose harness.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class ColorPickerDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `dialog mounts with preview swatch and SV square`() {
        composeRule.setContent {
            ColorPickerDialog(
                initialRgb = 0xFF8800L,
                onConfirm = {},
                onDismiss = {},
                onReset = {},
            )
        }
        composeRule.onNodeWithTag("color_picker_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("color_picker_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("color_picker_sv_square").assertIsDisplayed()
        composeRule.onNodeWithTag("color_picker_hue_slider").assertIsDisplayed()
    }

    @Test
    fun `confirm fires onConfirm with the seed RGB when the user does not edit`() {
        var confirmed: Long? = null
        composeRule.setContent {
            ColorPickerDialog(
                initialRgb = 0xFF8800L,
                onConfirm = { confirmed = it },
                onDismiss = {},
                onReset = {},
            )
        }
        composeRule.onNodeWithTag("color_picker_confirm").performClick()
        assertNotNull(confirmed)
        // HSV → RGB round-trip may shift by 1 per channel due to float
        // quantisation in the picker's preview, but for the canonical
        // 0xFF8800 (255,136,0) seed the maths is exact.
        assertEquals(0xFF8800L, confirmed)
    }

    @Test
    fun `cancel fires onDismiss`() {
        var dismissed = 0
        composeRule.setContent {
            ColorPickerDialog(
                initialRgb = 0xFF8800L,
                onConfirm = {},
                onDismiss = { dismissed++ },
                onReset = {},
            )
        }
        composeRule.onNodeWithTag("color_picker_cancel").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `reset button fires onReset when provided`() {
        var resets = 0
        composeRule.setContent {
            ColorPickerDialog(
                initialRgb = 0xFF8800L,
                onConfirm = {},
                onDismiss = {},
                onReset = { resets++ },
            )
        }
        // `assertExists` rather than `assertIsDisplayed` — the AlertDialog
        // body is scrollable and the Reset row sits below the fold in the
        // Robolectric viewport. Existence in the semantics tree is what
        // we actually care about; performClick scrolls to + dispatches
        // against an existing-but-offscreen node correctly.
        composeRule.onNodeWithTag("color_picker_reset").assertExists()
        composeRule.onNodeWithTag("color_picker_reset").performClick()
        assertEquals(1, resets)
    }

    @Test
    fun `reset button is hidden when onReset is null`() {
        composeRule.setContent {
            ColorPickerDialog(
                initialRgb = 0xFF8800L,
                onConfirm = {},
                onDismiss = {},
                onReset = null,
            )
        }
        composeRule.onNodeWithTag("color_picker_reset").assertDoesNotExist()
    }

    @Test
    fun `rgbToHsv and colorToRgbLong round-trip a canonical seed`() {
        // 0xFF8800 → orange. The picker stores 24-bit RGB exactly; HSV
        // is the intermediate space, not the source-of-truth. We don't
        // need a Compose harness to exercise this — the helpers are
        // pure Kotlin.
        val rgb = 0xFF8800L
        val hsv = rgbToHsv(rgb)
        val back = colorToRgbLong(androidx.compose.ui.graphics.Color.hsv(hsv[0], hsv[1], hsv[2]))
        assertEquals(rgb, back)
    }
}

