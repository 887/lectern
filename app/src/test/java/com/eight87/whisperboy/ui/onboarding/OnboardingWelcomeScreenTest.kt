package com.eight87.whisperboy.ui.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.eight87.whisperboy.ui.settings.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase L.1 — Robolectric Compose coverage for [OnboardingWelcomeScreen].
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class OnboardingWelcomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders title body and CTA`() {
        composeRule.setContent {
            OnboardingWelcomeScreen(onGetStarted = {})
        }
        composeRule.onNodeWithText("Welcome to whisperboy").assertExists()
        composeRule.onNodeWithText("Whisperboy plays audiobooks from folders you pick.").assertExists()
        composeRule.onNodeWithText("Get started").assertExists()
    }

    @Test
    fun `CTA tap fires onGetStarted`() {
        var fires = 0
        composeRule.setContent {
            OnboardingWelcomeScreen(onGetStarted = { fires++ })
        }
        composeRule.onNodeWithText("Get started").performClick()
        assertEquals(1, fires)
    }
}
