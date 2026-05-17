package com.eight87.whisperboy.ui.settings.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.whisperboy.theme.CategoryAccent
import com.eight87.whisperboy.theme.accentFor

/**
 * Dimensions used by every settings card / row. Ported from tonearmboy's
 * `SettingsDimens` — keeping these as top-level constants makes layout
 * consistent and the "sitting in the middle" 16-dp horizontal page inset
 * easy to read at the call site.
 */
object SettingsDimens {
    val PagePadding = 16.dp
    val CardCornerRadius = 16.dp
    val RowVerticalPadding = 14.dp
    val RowHorizontalPadding = 16.dp
    val IconSize = 24.dp
    val IconLabelGap = 16.dp
    val GroupTitleTopPadding = 20.dp
    val GroupTitleBottomPadding = 8.dp
    val CardSpacing = 16.dp
    val AvatarSize = 40.dp
}

/**
 * Material 3 card hosting a group of related rows. Renders with
 * `RoundedCornerShape(16.dp)` corners and a `surfaceContainerHigh`
 * tonal background so the card sits visually above the page —
 * the "sitting in the middle" pattern from Android 16 system Settings.
 *
 * @param title Optional small header rendered above the card in
 *   `primary` (the orange/accent in the M3E palette). Lined up with
 *   the card's leading edge.
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = SettingsDimens.RowHorizontalPadding,
                    top = SettingsDimens.GroupTitleTopPadding,
                    bottom = SettingsDimens.GroupTitleBottomPadding,
                ),
            )
        }
        Card(
            shape = RoundedCornerShape(SettingsDimens.CardCornerRadius),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "settings_card" },
        ) {
            Column { content() }
        }
    }
}

/**
 * One row inside a [SettingsCard]. Always carries a leading icon
 * (the Android Settings convention), label, optional subtitle, and
 * an optional trailing widget slot for switches / colour swatches /
 * value chips. Tapping anywhere on the row fires [onClick].
 *
 * When [accent] is null but [id] is non-null, the accent is auto-
 * derived via [accentFor] so every catalog row gets a coloured avatar
 * without each call site having to pass it explicitly.
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    id: String? = null,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    accent: CategoryAccent? = null,
) {
    val resolvedAccent = accent ?: id?.let { accentFor(it) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(
                horizontal = SettingsDimens.RowHorizontalPadding,
                vertical = SettingsDimens.RowVerticalPadding,
            )
            .heightIn(min = 48.dp)
            .semantics { testTag = "settings_row_${id ?: label}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (resolvedAccent != null) {
            Box(
                modifier = Modifier
                    .size(SettingsDimens.AvatarSize)
                    .clip(CircleShape)
                    .background(resolvedAccent.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = resolvedAccent.onContainer,
                    modifier = Modifier.size(SettingsDimens.IconSize),
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SettingsDimens.IconSize),
            )
        }
        Spacer(Modifier.size(SettingsDimens.IconLabelGap))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.size(8.dp))
            Box { trailing() }
        }
    }
}

/**
 * Toggle row variant. Tapping anywhere on the row flips the state, and
 * the trailing [Switch] follows.
 */
@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    id: String? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    accent: CategoryAccent? = null,
) {
    SettingsRow(
        id = id,
        icon = icon,
        label = label,
        subtitle = subtitle,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else null,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
            )
        },
        modifier = modifier,
        accent = accent,
    )
}

/**
 * Subtle divider used between rows inside a [SettingsCard]. Indented to
 * align with the row text past the avatar/icon column.
 */
@Composable
fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(
            start = SettingsDimens.RowHorizontalPadding +
                SettingsDimens.AvatarSize +
                SettingsDimens.IconLabelGap,
        ),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
