@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.eight87.whisperboy.theme

import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * m3-expressive Phase B.3 — regression guard for the surface-tier
 * ladder.
 *
 * The whole M3 Expressive look depends on `surface` being a strictly
 * different tone from `surfaceContainer` / `surfaceContainerHigh` so
 * the page background and grouped-card backgrounds *visually
 * separate without shadow elevation*. If a future Material 3 / BOM
 * upgrade ever collapses the ladder back into a single tone (it has
 * happened in alpha churn before), the whole grouped-card look goes
 * flat — but unit-test-free because the chrome still compiles.
 *
 * This test asserts the tones differ in both modes so a build that
 * silently re-collapses the ladder fails CI instead of shipping a
 * flat-looking app.
 */
class SurfaceTierLadderTest {

  @Test fun `dark surface differs from surfaceContainer`() {
    assertNotEquals(DarkColorScheme.surface, DarkColorScheme.surfaceContainer)
  }

  @Test fun `dark surface differs from surfaceContainerHigh`() {
    // The doctrine in `docs/plans/m3-expressive.md` gotcha #2 says
    // AMOLED-leaning dark should reach for `surfaceContainerHigh` for
    // grouped cards / mini-player peek / sheet body — that decision
    // is only useful if the tone is distinct from `surface`.
    assertNotEquals(DarkColorScheme.surface, DarkColorScheme.surfaceContainerHigh)
  }

  @Test fun `light surface differs from surfaceContainer`() {
    assertNotEquals(LightColorScheme.surface, LightColorScheme.surfaceContainer)
  }
}
