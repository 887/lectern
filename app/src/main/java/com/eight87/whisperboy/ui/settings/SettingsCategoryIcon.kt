package com.eight87.whisperboy.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.eight87.whisperboy.theme.CategoryAccent

/**
 * m3-expressive Phase C — coloured circular row-icon avatar.
 *
 * Renders [icon] inside a 40-dp [CircleShape] tile tinted with
 * `accent.container`, with the glyph painted in `accent.onContainer`
 * at 24 dp. This is the "happy colours" Settings look from the
 * Android 16 system Settings — flat avatars at every row, no
 * elevation, hand-picked per-category hue.
 *
 * The composable is deliberately tiny: a [Box] for the circle, an
 * [Icon] inside. The hand-pickedness lives in `CategoryAccent.kt`;
 * the use-site lives in `SettingsScreen` / `AboutScreen`.
 *
 * Callers should pass **filled** icons (`Icons.Filled.*`) here, per
 * gotcha #4 of the four-patterns section of
 * `docs/plans/m3-expressive.md` — outlined glyphs read weak inside
 * a coloured circle.
 */
@Composable
fun SettingsCategoryIcon(
  icon: ImageVector,
  accent: CategoryAccent,
  contentDescription: String?,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .size(40.dp)
      .clip(CircleShape)
      .background(accent.container),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = accent.onContainer,
      modifier = Modifier.size(24.dp),
    )
  }
}
