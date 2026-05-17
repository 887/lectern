package com.eight87.whisperboy.theme

import androidx.compose.ui.graphics.Color

/**
 * m3-expressive Phase C — pair of `(container, onContainer)` colours
 * for the per-row coloured circle row-icon avatars. Mirrors Material
 * 3's `primaryContainer` / `onPrimaryContainer` token shape so the
 * runtime use site reads identically to a built-in colour role.
 *
 * Hand-picked palette covering whisperboy's category split (Playback,
 * Sleep timer, Library, Theme, About, Open-source licenses). The
 * mapping from row id to accent is intentionally explicit — it makes
 * "the orange one is Playback" stable across sessions / app reinstalls
 * and lets a future hand-rolled surface (chapter list, bookmarks
 * sheet) reach for a known category accent without re-rolling the
 * hash.
 *
 * The accents are NOT driven off `dynamicDarkColorScheme()` —
 * letting the user's wallpaper steamroll the per-category intent
 * defeats the colour-coding entirely. Hand-picked stays hand-picked.
 *
 * Container tones land around lightness 22-28 % so the circle reads
 * as a quietly-saturated tile against the page; on-container tones
 * land at lightness 75-85 % for high-contrast filled glyphs. The
 * pairs were spot-checked for WCAG AA contrast at the 24-dp icon
 * size (~3:1 minimum for non-text glyphs); every pair clears.
 */
data class CategoryAccent(
  val container: Color,
  val onContainer: Color,
)

// Six hand-picked accent pairs covering whisperboy's category split.
// Each is exposed top-level so non-settings surfaces (e.g. a future
// chapter list, bookmark sheet) can reach for a known accent
// directly without going through the id-keyed `accentFor`.

/** Playback — orange. Warm "in motion" tone, matches the system Display category. */
val PlaybackAccent: CategoryAccent = CategoryAccent(
  container = Color(0xFF5C3300),
  onContainer = Color(0xFFFFB877),
)

/** Sleep timer — indigo. Cool, sleep-adjacent. */
val SleepTimerAccent: CategoryAccent = CategoryAccent(
  container = Color(0xFF1F2A6E),
  onContainer = Color(0xFFB8C4FF),
)

/** Library — green. "Collection / shelves" connotation. */
val LibraryAccent: CategoryAccent = CategoryAccent(
  container = Color(0xFF1F4D2E),
  onContainer = Color(0xFF9CDDB4),
)

/** Theme — purple. Matches the system Apps / Theme category vibe. */
val ThemeAccent: CategoryAccent = CategoryAccent(
  container = Color(0xFF3F2C73),
  onContainer = Color(0xFFD0BCFF),
)

/** About — teal. Calm, informational. */
val AboutAccent: CategoryAccent = CategoryAccent(
  container = Color(0xFF003D40),
  onContainer = Color(0xFF7FE0E6),
)

/** Open-source licenses — slate. Neutral, document-like. */
val LicensesAccent: CategoryAccent = CategoryAccent(
  container = Color(0xFF2E3A47),
  onContainer = Color(0xFFB8C7D6),
)

/**
 * Map every category accent by its row id. Public + top-level so
 * non-settings surfaces (and any test that wants to enumerate the
 * palette) can read it. Keys mirror the [accentFor] `when` cases.
 */
val darkAccents: Map<String, CategoryAccent> = mapOf(
  "playback" to PlaybackAccent,
  "sleep" to SleepTimerAccent,
  "library" to LibraryAccent,
  "theme" to ThemeAccent,
  "about" to AboutAccent,
  "licenses" to LicensesAccent,
)

/**
 * Pick an accent for the row identified by [id]. Top-level / not
 * `@Composable` so non-Compose callers (and tests) can use it. Per
 * gotcha #3 in `docs/plans/m3-expressive.md`, every row-id-bearing
 * surface should resolve its accent through this function so a
 * hand-rolled screen (About, Licenses, future custom surfaces) gets
 * the same colouring as the catalog-driven Settings rows.
 *
 * Unknown ids fall back to [PlaybackAccent] so a typo / future-added
 * row still renders something rather than going monochrome.
 */
fun accentFor(id: String): CategoryAccent = when (id) {
  "playback" -> PlaybackAccent
  "sleep" -> SleepTimerAccent
  "library" -> LibraryAccent
  "theme" -> ThemeAccent
  "about" -> AboutAccent
  "licenses" -> LicensesAccent
  else -> PlaybackAccent
}
