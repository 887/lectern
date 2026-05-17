package com.eight87.whisperboy.ui.settings

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * oss-licenses C.2 — Compose UI test for [LicensesScreen] under Robolectric.
 *
 * Asserts the screen mounts against the real `assets/licenses/artifacts.json`
 * (packaged into the test classpath via `unitTests.isIncludeAndroidResources`)
 * and renders the lazy column with a non-empty inventory. The asset-loader
 * itself is exercised directly to validate the parse path; the Compose tree is
 * mounted to validate the screen actually wires up without crashing.
 */
/**
 * Override the Robolectric Application so we don't construct the real
 * [com.eight87.whisperboy.WhisperboyApplication] (whose `onCreate` builds the
 * [com.eight87.whisperboy.AppGraph] and asynchronously binds the Media3
 * `MediaController` against the not-running `MediaLibraryService` — that bind
 * resolves to a null `ComponentName` and the test thread NPEs while the
 * Compose harness drains the looper).
 */
class TestApplication : Application()

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class LicensesScreenTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `loadLicensesFromAssets returns a non-empty inventory`() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val entries = loadLicensesFromAssets(context)
    assertTrue(
      "Expected the packaged artifacts.json to yield at least one license entry, got 0",
      entries.isNotEmpty(),
    )
    // Every entry should have a non-blank coordinate triple — sanity-check the
    // parse path rather than relying on Compose-tree assertions alone.
    entries.forEach { e ->
      assertTrue("groupId blank for $e", e.groupId.isNotBlank())
      assertTrue("artifactId blank for $e", e.artifactId.isNotBlank())
      assertTrue("version blank for $e", e.version.isNotBlank())
    }
  }

  @Test
  fun `LicensesScreen renders the lazy column with a non-empty list`() {
    composeRule.setContent {
      LicensesScreen(onBack = {})
    }
    composeRule.onNodeWithTag("licenses_screen").assertExists()
    // The empty-state column should NOT exist when the inventory is non-empty.
    composeRule.onNodeWithTag("licenses_empty").assertDoesNotExist()
  }
}
