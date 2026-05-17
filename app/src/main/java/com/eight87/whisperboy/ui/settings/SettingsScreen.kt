package com.eight87.whisperboy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.whisperboy.data.library.LibraryRescanCoordinator
import com.eight87.whisperboy.data.library.RescanState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.whisperboy.R

/**
 * Phase K.1 scaffold — entry surface for settings.
 *
 * Renders two grouped cards:
 *
 *   - "General" card: Playback / Sleep timer / Library / Theme rows. The
 *     subcategory screens for these land in K.2 / K.3 / K.4 / K.5; until
 *     then taps show a "Coming soon" snackbar.
 *   - "About" card: the About row, which navigates to [AboutScreen].
 *
 * Surface tier ladder follows the M3E plan: page background uses
 * `MaterialTheme.colorScheme.surface`, cards use `surfaceContainerHigh`.
 *
 * The row layout (icon + title + subtitle, full-width clickable) is
 * deliberately whisperboy-fresh rather than ported from tonearmboy's
 * catalog infrastructure — that catalog brings sub-page sharing,
 * search, accent derivation and several other affordances that
 * whisperboy doesn't need yet. A Phase C inch from `m3-expressive.md`
 * can later wrap the leading icon in a `CategoryAccent` coloured
 * circular avatar without restructuring rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    libraryRescanCoordinator: LibraryRescanCoordinator,
    onBack: () -> Unit,
    onAboutClick: () -> Unit,
    onLibraryFoldersClick: () -> Unit,
    onPlaybackClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onThemeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rescanState by libraryRescanCoordinator.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_cd),
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
            SettingsCard {
                SettingsCategoryRow(
                    icon = Icons.Outlined.PlayCircle,
                    title = stringResource(R.string.settings_category_playback),
                    subtitle = stringResource(R.string.settings_category_playback_subtitle),
                    onClick = onPlaybackClick,
                )
                SettingsCategoryRow(
                    icon = Icons.Outlined.Bedtime,
                    title = stringResource(R.string.settings_category_sleep_timer),
                    subtitle = stringResource(R.string.settings_category_sleep_timer_subtitle),
                    onClick = onSleepTimerClick,
                )
                SettingsCategoryRow(
                    icon = Icons.Outlined.FolderOpen,
                    title = stringResource(R.string.settings_category_library),
                    subtitle = stringResource(R.string.settings_category_library_subtitle),
                    onClick = onLibraryFoldersClick,
                )
                // "Rescan now" — Phase K.4 partial. Sits inside the General card under
                // the Library row so it's discoverable next to folder management. Disabled
                // while a scan is in flight (RescanState.Running).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Button(
                        onClick = { libraryRescanCoordinator.requestRescan() },
                        enabled = rescanState !is RescanState.Running,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_rescan_now))
                    }
                }
                SettingsCategoryRow(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.settings_category_theme),
                    subtitle = stringResource(R.string.settings_category_theme_subtitle),
                    onClick = onThemeClick,
                )
            }

            SettingsCard {
                SettingsCategoryRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_category_about),
                    subtitle = stringResource(R.string.settings_category_about_subtitle),
                    onClick = onAboutClick,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
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

/**
 * One settings category row. Icon is a plain [Icon] for now; the M3E
 * Phase C inch (per `docs/plans/m3-expressive.md`) can later wrap this
 * in a `CategoryAccent` coloured circular avatar by editing this single
 * composable.
 */
@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
