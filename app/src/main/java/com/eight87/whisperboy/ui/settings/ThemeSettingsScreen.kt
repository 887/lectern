package com.eight87.whisperboy.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.whisperboy.R
import com.eight87.whisperboy.data.theme.ThemeMode
import com.eight87.whisperboy.data.theme.ThemeSettings
import kotlinx.coroutines.launch

/**
 * Phase K.5 — theme settings sub-page.
 *
 * Two cards:
 *
 *   1. **Theme mode** — radio group: Light / Dark / Follow system.
 *   2. **Dynamic color** — Material-You toggle. Disabled on API < 31; the
 *      subtitle then reads "Requires Android 12 or later" so the row stays
 *      discoverable (greyed, not hidden — Voice / system-Settings pattern).
 *
 * Surfaces match Settings/About: page bg = `surface`, cards =
 * `surfaceContainerHigh`, `defaultElevation = 0.dp`.
 *
 * Writes are launched into [rememberCoroutineScope] — the DataStore setters
 * are `suspend` but cheap (single preference key edit); no need for a VM.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    themeSettings: ThemeSettings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val mode by themeSettings.mode.collectAsStateWithLifecycle(initialValue = ThemeMode.FollowSystem)
    val dynamicColor by themeSettings.dynamicColor.collectAsStateWithLifecycle(initialValue = true)

    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_theme_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_theme_back_cd),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ThemeCard {
                SectionLabel(stringResource(R.string.settings_theme_mode_section))
                ThemeMode.entries.forEach { entry ->
                    ThemeModeRow(
                        label = entry.label(),
                        selected = mode == entry,
                        onSelect = { scope.launch { themeSettings.setMode(entry) } },
                    )
                }
            }

            ThemeCard {
                DynamicColorRow(
                    enabled = dynamicColorSupported,
                    checked = dynamicColor && dynamicColorSupported,
                    onCheckedChange = { newValue ->
                        scope.launch { themeSettings.setDynamicColor(newValue) }
                    },
                )
            }
        }
    }
}

@Composable
private fun ThemeMode.label(): String = when (this) {
    ThemeMode.Light -> stringResource(R.string.settings_theme_mode_light)
    ThemeMode.Dark -> stringResource(R.string.settings_theme_mode_dark)
    ThemeMode.FollowSystem -> stringResource(R.string.settings_theme_mode_system)
}

@Composable
private fun ThemeCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            content = { content() },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ThemeModeRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            // Click is owned by the whole-row `selectable` above so the
            // RadioButton itself doesn't double-handle the gesture.
            onClick = null,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DynamicColorRow(
    enabled: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val titleColor =
        if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val bodyColor =
        if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)

    val rowClick = Modifier.takeIf { enabled }
        ?.let { Modifier.clickable { onCheckedChange(!checked) } }
        ?: Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_theme_dynamic_color_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    if (enabled) R.string.settings_theme_dynamic_color_body
                    else R.string.settings_theme_dynamic_color_unavailable,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = bodyColor,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
        )
    }
}
