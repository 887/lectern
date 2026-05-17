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
import androidx.compose.runtime.getValue
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
 * Phase K.5 — reads the user's theme mode + dynamic-color preference
 * from [ThemeSettings] and applies them. The mode/flag flow into
 * `colorScheme` selection here; everything below stays unchanged from
 * the M3E A.3 wiring (MaterialExpressiveTheme + Typography).
 *
 * Initial values mirror the persisted defaults
 * ([ThemeMode.FollowSystem], dynamic-color = `true`) so first-frame
 * rendering before DataStore emits matches the steady-state default.
 */
@Composable
fun WhisperboyTheme(
  themeSettings: ThemeSettings,
  content: @Composable () -> Unit,
) {
  val mode by themeSettings.mode.collectAsStateWithLifecycle(initialValue = ThemeMode.FollowSystem)
  val dynamicColor by themeSettings.dynamicColor.collectAsStateWithLifecycle(initialValue = true)

  val darkTheme = when (mode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.FollowSystem -> isSystemInDarkTheme()
  }

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
