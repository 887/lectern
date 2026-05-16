@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.eight87.whisperboy.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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

@Composable
fun WhisperboyTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

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
