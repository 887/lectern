package com.eight87.whisperboy.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression guards for the album-art tint plumbing.
 *
 * 1. [AlbumPalette.Empty] carries a `null` `surfaceTint` — composables
 *    using the default `LocalAlbumPalette` see "no tint applied".
 * 2. [blendSurface] is a passthrough at `alpha = 0` (returns the base
 *    color) and full-tint at `alpha = 1`. Guards against a regression
 *    that would silently override surfaces with the tint regardless of
 *    the configured blend strength.
 * 3. [blendSurface] at the canonical 0.12 fraction (used for
 *    `surface` in [WhisperboyTheme]) lands strictly between base and
 *    tint — a sanity check that the linear-RGB math hasn't drifted.
 */
class AlbumPaletteTest {

  @Test
  fun `AlbumPalette Empty has null surface tint`() {
    assertNull(AlbumPalette.Empty.surfaceTint)
  }

  @Test
  fun `blendSurface alpha zero returns base`() {
    val base = Color(red = 0.2f, green = 0.3f, blue = 0.4f, alpha = 1f)
    val tint = Color(red = 0.9f, green = 0.1f, blue = 0.5f, alpha = 1f)
    val result = blendSurface(base, tint, 0f)
    assertEquals(base.red, result.red, 1e-4f)
    assertEquals(base.green, result.green, 1e-4f)
    assertEquals(base.blue, result.blue, 1e-4f)
  }

  @Test
  fun `blendSurface alpha one returns tint`() {
    val base = Color(red = 0.2f, green = 0.3f, blue = 0.4f, alpha = 1f)
    val tint = Color(red = 0.9f, green = 0.1f, blue = 0.5f, alpha = 1f)
    val result = blendSurface(base, tint, 1f)
    assertEquals(tint.red, result.red, 1e-4f)
    assertEquals(tint.green, result.green, 1e-4f)
    assertEquals(tint.blue, result.blue, 1e-4f)
  }

  @Test
  fun `blendSurface canonical 0_12 fraction lands between base and tint`() {
    val base = Color(red = 0.10f, green = 0.10f, blue = 0.10f, alpha = 1f)
    val tint = Color(red = 0.90f, green = 0.90f, blue = 0.90f, alpha = 1f)
    val result = blendSurface(base, tint, 0.12f)
    // 0.10 * 0.88 + 0.90 * 0.12 = 0.196
    assertEquals(0.196f, result.red, 1e-4f)
    assertNotEquals(base.red, result.red)
    assertNotEquals(tint.red, result.red)
  }
}
