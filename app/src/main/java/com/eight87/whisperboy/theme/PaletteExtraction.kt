package com.eight87.whisperboy.theme

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extract a single dominant accent [Color] from the book's cover bitmap.
 *
 * Lifted out of `ui/playback/PaletteTint.kt` (originally Phase F.6 for
 * the player-only gradient) so the new theme-level
 * [rememberPlayingBookPalette] can share the same extraction. Single
 * source of truth — the player gradient and the whole-app chrome blend
 * agree on what "the book's color" is.
 *
 * Discipline:
 *  - **Off-main.** Palette decode + quantization are IO-bound; this is a `suspend` fun
 *    pinned to [Dispatchers.IO] (cold-start-perf doctrine — never block the UI thread
 *    on bitmap work).
 *  - **Tile decode at 1/4 res.** Cover files are already ~600x600 from the iTunes /
 *    folder / DDG sources; [Palette]'s histogram doesn't need the full bitmap — 150x150
 *    is plenty for swatch extraction and saves ~16x the pixels.
 *  - **Swatch fallback ladder.** Prefer [Palette.getVibrantSwatch] (the "real" accent),
 *    then [Palette.getDarkVibrantSwatch] (low-key covers), then [Palette.getDominantSwatch]
 *    (greyscale / monochrome covers). Returns `null` if none of those produce a swatch.
 *  - **Null-safe.** Missing path, missing file, or a corrupt bitmap all return `null`.
 */
suspend fun extractTint(coverPath: String?): Color? = withContext(Dispatchers.IO) {
  val path = coverPath ?: return@withContext null
  val options = BitmapFactory.Options().apply { inSampleSize = 4 }
  val bitmap = runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
    ?: return@withContext null
  val palette = runCatching { Palette.from(bitmap).generate() }.getOrNull()
    ?: return@withContext null
  val swatch = palette.vibrantSwatch
    ?: palette.darkVibrantSwatch
    ?: palette.dominantSwatch
  swatch?.rgb?.let { Color(it) }
}
