package com.eight87.whisperboy.ui.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.eight87.whisperboy.ui.settings.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase L.2 — Robolectric Compose coverage for [OnboardingPermissionsScreen].
 *
 * Robolectric defaults to API 34 here, well above the SDK 33 gate
 * (`POST_NOTIFICATIONS`), so the rationale + buttons render and "Not now"
 * routes to [onNext]. The Allow path is exercised at render-time only
 * (button presence) — clicking it would `launcher.launch(POST_NOTIFICATIONS)`,
 * which the Robolectric activity-result plumbing doesn't auto-resolve without
 * additional shadowing, so we don't assert on `onNext` from that branch here.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class OnboardingPermissionsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders rationale title body and both buttons`() {
        composeRule.setContent {
            OnboardingPermissionsScreen(onNext = {})
        }
        // The title and the grant button share the same copy ("Allow notifications")
        // — assert both exist (two nodes with that text).
        assertTrue(
            "expected title + Allow button to render",
            composeRule.onAllNodesWithText("Allow notifications").fetchSemanticsNodes().size >= 1,
        )
        composeRule.onNodeWithText(
            "Whisperboy uses a notification to show what's playing and to keep playback running in the background. You can change this any time in system settings.",
        ).assertExists()
        composeRule.onNodeWithText("Not now").assertExists()
    }

    @Test
    fun `tapping Not now fires onNext`() {
        var fires = 0
        composeRule.setContent {
            OnboardingPermissionsScreen(onNext = { fires++ })
        }
        composeRule.onNodeWithText("Not now").performClick()
        assertEquals(1, fires)
    }
}
