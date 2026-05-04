package com.eight87.whisperboy.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM-only tests for the easter-egg state machine. Drives a synthetic clock via the
 * `nowMillis` parameter so the 5-second window is exercised without `Thread.sleep`.
 */
class EasterEggControllerTest {

    @Test
    fun `first tap surfaces the first prompt`() {
        val controller = EasterEggController()
        assertEquals(EasterEggController.Outcome.FirstPromptSnackbar, controller.tap(nowMillis = 0L))
    }

    @Test
    fun `second tap inside the window surfaces the second prompt`() {
        val controller = EasterEggController()
        controller.tap(nowMillis = 0L)
        assertEquals(EasterEggController.Outcome.SecondPromptSnackbar, controller.tap(nowMillis = 1_000L))
    }

    @Test
    fun `third tap inside the window reveals`() {
        val controller = EasterEggController()
        controller.tap(nowMillis = 0L)
        controller.tap(nowMillis = 1_000L)
        assertEquals(EasterEggController.Outcome.Reveal, controller.tap(nowMillis = 2_000L))
    }

    @Test
    fun `reveal resets the counter for repeatability`() {
        val controller = EasterEggController()
        controller.tap(nowMillis = 0L)
        controller.tap(nowMillis = 1_000L)
        controller.tap(nowMillis = 2_000L) // reveal
        assertEquals(0, controller.debugCount())
        // Next tap is a fresh first-prompt.
        assertEquals(EasterEggController.Outcome.FirstPromptSnackbar, controller.tap(nowMillis = 3_000L))
    }

    @Test
    fun `tap arriving outside the window resets the counter`() {
        val controller = EasterEggController()
        controller.tap(nowMillis = 0L) // first
        controller.tap(nowMillis = 4_999L) // second, just inside
        // Now wait past the window from the LAST tap.
        assertEquals(
            EasterEggController.Outcome.FirstPromptSnackbar,
            controller.tap(nowMillis = 4_999L + 5_001L),
        )
    }

    @Test
    fun `custom window is honoured`() {
        val controller = EasterEggController(windowMillis = 1_000L)
        controller.tap(nowMillis = 0L)
        // 1 ms past the custom window — should reset.
        assertEquals(
            EasterEggController.Outcome.FirstPromptSnackbar,
            controller.tap(nowMillis = 1_001L),
        )
    }
}
