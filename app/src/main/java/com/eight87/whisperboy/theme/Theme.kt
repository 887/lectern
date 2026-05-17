@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.eight87.whisperboy.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.whisperboy.data.theme.ThemeMode
import com.eight87.whisperboy.data.theme.ThemeSettings

// m3-expressive A.4 / B.1 — fall through to the expressive seed
// schemes. They produce brighter container pairs (`primaryContainer` /
// `secondaryContainer` / `tertiaryContainer`) and the wider
// surface-tier ladder (`surfaceContainerLow…High`) the M3E look needs.
// `expressiveDarkColorScheme()` doesn't exist in 1.5.0-alpha18 — only
// the light variant ships — so we pair `expressiveLightColorScheme()`
// for light mode with `darkColorScheme()` plus the brand seeds for
// dark mode. Both still produce the full surface-tier ladder, which
// the surface-tier audit (Phase B) relies on.
internal val DarkColorScheme = darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

internal val LightColorScheme = expressiveLightColorScheme()

/**
 * CompositionLocal carrying the user-picked "chrome tint" override
 * (ported from tonearmboy's D.25.1 work). `null` = unset, surfaces
 * fall through to their natural tint source (the Palette-from-cover
 * extraction on `PlaybackScreen`, the static brand colours elsewhere).
 * Non-null = "the user explicitly wants this colour everywhere a
 * surface consults a chrome tint."
 *
 * Lives as a `staticCompositionLocalOf` rather than threaded through
 * every composable that reads it because the value is set once at the
 * theme root (`WhisperboyTheme`) and changes only when the user edits
 * the settings row — a static local matches that "rarely changes,
 * read in many leaf places" shape exactly.
 */
val LocalCustomChromeTint = staticCompositionLocalOf<Color?> { null }

/**
 * CompositionLocal flagging whether `PlaybackScreen`'s Palette-from-
 * cover-art tint should be applied. Default `true` — pre-existing
 * behaviour. Wired at the theme root from `ThemeSettings.tintChromeByAlbumArt`.
 * Surface-level consumers (today: `PlaybackScreen`) skip Palette
 * extraction / fall back to the static surface gradient when this is
 * `false`. `LocalCustomChromeTint` (when set) still overrides everything.
 */
val LocalTintByAlbumArt = staticCompositionLocalOf { true }

/**
 * Phase K.5 — reads the user's theme mode + dynamic-color preference
 * from [ThemeSettings] and applies them. The mode/flag flow into
 * `colorScheme` selection here; everything below stays unchanged from
 * the M3E A.3 wiring (MaterialExpressiveTheme + Typography).
 *
 * Two custom-colour knobs (K.5 follow-up — ported from tonearmboy
 * `82d6248`) layer on top of the mode/dynamic-color axes:
 *
 *  - `customBaseSeed`: when non-zero, becomes the Material 3 seed for
 *    both light and dark schemes regardless of mode + dynamic-color.
 *    Overrides Material You and the static fallback both — the user
 *    has explicitly said "I want a palette derived from this colour".
 *  - `customChromeTint`: when non-zero, published via
 *    [LocalCustomChromeTint] for consumers (today: `PlaybackScreen`'s
 *    background gradient) to override their cover-derived tint with.
 *
 * Initial values mirror the persisted defaults
 * ([ThemeMode.FollowSystem], dynamic-color = `true`, both custom
 * colours unset / `0L`) so first-frame rendering before DataStore
 * emits matches the steady-state default.
 */
@Composable
fun WhisperboyTheme(
  themeSettings: ThemeSettings,
  content: @Composable () -> Unit,
) {
  val mode by themeSettings.mode.collectAsStateWithLifecycle(initialValue = ThemeMode.FollowSystem)
  val dynamicColor by themeSettings.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
  val customBaseSeed by themeSettings.customBaseSeed.collectAsStateWithLifecycle(initialValue = 0L)
  val customChromeTint by themeSettings.customChromeTint.collectAsStateWithLifecycle(initialValue = 0L)
  val tintByAlbumArt by themeSettings.tintChromeByAlbumArt.collectAsStateWithLifecycle(initialValue = true)

  val darkTheme = when (mode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.FollowSystem -> isSystemInDarkTheme()
  }

  // Override order: customBaseSeed (when set) wins outright, otherwise
  // dynamic-color on API 31+, otherwise the static fallback.
  val colorScheme = if (customBaseSeed != 0L) {
    deriveCustomScheme(customBaseSeed, darkTheme)
  } else when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  val chromeTint: Color? = if (customChromeTint == 0L) null else colorFromRgbLong(customChromeTint)

  CompositionLocalProvider(
    LocalCustomChromeTint provides chromeTint,
    LocalTintByAlbumArt provides tintByAlbumArt,
  ) {
    // m3-expressive A.3 — `MaterialExpressiveTheme` pulls in the new
    // motion / typography / shape defaults (rounded extra-large group
    // shapes, faster spring-based motion). It still resolves
    // `MaterialTheme.colorScheme` against whatever scheme we hand in.
    //
    // cold-start-perf B.2 — no per-color `animateColorAsState` chains
    // here. Theme is a pure function call; recomposing on dynamic-color
    // change is cheap. The animated-tint pattern is a tonearmboy thing
    // (album-art palette blending) that whisperboy has no need for —
    // audiobook chrome doesn't shift per-track.
    MaterialExpressiveTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}

/**
 * Derive a Material 3 [ColorScheme] from a 24-bit RGB seed (ported
 * from tonearmboy's D.25.1 work).
 *
 * Strategy: build primary / secondary / tertiary tonal anchors by
 * shifting the seed's hue (secondary = +30°, tertiary = +60°) and
 * lightness, then plug them into the canonical
 * `lightColorScheme` / `darkColorScheme` factories. This sidesteps
 * Material 3's `dynamicColorScheme(seed, isDark)` (added in 1.4) so
 * the build works regardless of the active Material 3 version.
 *
 * Pure helper for unit-testability — no Compose runtime required.
 */
internal fun deriveCustomScheme(seedRgb: Long, darkTheme: Boolean): ColorScheme {
  val primary = colorFromRgbLong(seedRgb)
  val (h, s, _) = rgbToHslTriple(primary)
  val secondary = hslColor(((h + 30f) % 360f), (s * 0.7f).coerceIn(0f, 1f), if (darkTheme) 0.7f else 0.45f)
  val tertiary = hslColor(((h + 60f) % 360f), (s * 0.6f).coerceIn(0f, 1f), if (darkTheme) 0.7f else 0.5f)
  val primaryDark = hslColor(h, s, if (darkTheme) 0.7f else 0.4f)
  val onPrimary = if (luminance(primaryDark) > 0.5f) Color.Black else Color.White
  return if (darkTheme) {
    darkColorScheme(
      primary = primaryDark,
      secondary = secondary,
      tertiary = tertiary,
      onPrimary = onPrimary,
    )
  } else {
    lightColorScheme(
      primary = primaryDark,
      secondary = secondary,
      tertiary = tertiary,
      onPrimary = onPrimary,
    )
  }
}

internal fun colorFromRgbLong(rgb: Long): Color {
  val r = ((rgb shr 16) and 0xFFL).toInt()
  val g = ((rgb shr 8) and 0xFFL).toInt()
  val b = (rgb and 0xFFL).toInt()
  return Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = 1f)
}

/** Returns (hue 0..360, saturation 0..1, lightness 0..1). */
internal fun rgbToHslTriple(c: Color): Triple<Float, Float, Float> {
  val r = c.red; val g = c.green; val b = c.blue
  val max = maxOf(r, g, b); val min = minOf(r, g, b)
  val l = (max + min) / 2f
  val delta = max - min
  if (delta == 0f) return Triple(0f, 0f, l)
  val s = if (l > 0.5f) delta / (2f - max - min) else delta / (max + min)
  val h = when (max) {
    r -> 60f * (((g - b) / delta) % 6f)
    g -> 60f * (((b - r) / delta) + 2f)
    else -> 60f * (((r - g) / delta) + 4f)
  }.let { if (it < 0f) it + 360f else it }
  return Triple(h, s, l)
}

internal fun hslColor(hue: Float, saturation: Float, lightness: Float): Color {
  val h = ((hue % 360f) + 360f) % 360f
  val s = saturation.coerceIn(0f, 1f)
  val l = lightness.coerceIn(0f, 1f)
  val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
  val hp = h / 60f
  val x = c * (1f - kotlin.math.abs((hp % 2f) - 1f))
  val (r1, g1, b1) = when {
    hp < 1f -> Triple(c, x, 0f)
    hp < 2f -> Triple(x, c, 0f)
    hp < 3f -> Triple(0f, c, x)
    hp < 4f -> Triple(0f, x, c)
    hp < 5f -> Triple(x, 0f, c)
    else -> Triple(c, 0f, x)
  }
  val m = l - c / 2f
  return Color(red = (r1 + m).coerceIn(0f, 1f), green = (g1 + m).coerceIn(0f, 1f), blue = (b1 + m).coerceIn(0f, 1f), alpha = 1f)
}

internal fun luminance(c: Color): Float {
  // Relative luminance per WCAG-ish; cheap enough for an on-color decision.
  return 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
}
