package com.eight87.whisperboy.ui.playback

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * F.6 — extract a single dominant accent [Color] from the book's cover bitmap so
 * [PlaybackScreen] can paint a gentle vertical gradient tinted by it.
 *
 * Discipline:
 *  - **Off-main.** Palette decode + quantization are IO-bound; this is a `suspend` fun
 *    pinned to [Dispatchers.IO] (cold-start-perf doctrine — never block the UI thread
 *    on bitmap work).
 *  - **Tile decode at 1/4 res.** Cover files are already ~600×600 from the iTunes /
 *    folder / DDG sources; [Palette]'s histogram doesn't need the full bitmap — 150×150
 *    is plenty for swatch extraction and saves ~16× the pixels.
 *  - **Swatch fallback ladder.** Prefer [Palette.getVibrantSwatch] (the "real" accent),
 *    then [Palette.getDarkVibrantSwatch] (low-key covers), then [Palette.getDominantSwatch]
 *    (greyscale / monochrome covers). Returns `null` if none of those produce a swatch,
 *    in which case the caller falls back to plain `MaterialTheme.colorScheme.surface`.
 *  - **Null-safe.** Missing path, missing file, or a corrupt bitmap all return `null`;
 *    the caller treats `null` as "no tint, plain surface".
 */
internal suspend fun extractTint(coverPath: String?): Color? = withContext(Dispatchers.IO) {
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
