package com.eight87.whisperboy.ui.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.eight87.whisperboy.theme.CategoryAccent
import com.eight87.whisperboy.theme.PlaybackAccent
import com.eight87.whisperboy.theme.accentFor
import kotlinx.coroutines.launch

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
    onSleepTimerClick: () -> Unit,
    onThemeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(R.string.settings_category_pending_snackbar)
    val pendingClick: () -> Unit = {
        scope.launch { snackbarHostState.showSnackbar(comingSoon) }
    }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    id = "playback",
                    icon = Icons.Filled.PlayCircle,
                    title = stringResource(R.string.settings_category_playback),
                    subtitle = stringResource(R.string.settings_category_playback_subtitle),
                    onClick = pendingClick,
                )
                SettingsCategoryRow(
                    id = "sleep",
                    icon = Icons.Filled.Bedtime,
                    title = stringResource(R.string.settings_category_sleep_timer),
                    subtitle = stringResource(R.string.settings_category_sleep_timer_subtitle),
                    onClick = onSleepTimerClick,
                )
                SettingsCategoryRow(
                    id = "library",
                    icon = Icons.Filled.FolderOpen,
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
                    id = "theme",
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.settings_category_theme),
                    subtitle = stringResource(R.string.settings_category_theme_subtitle),
                    onClick = onThemeClick,
                )
            }

            SettingsCard {
                SettingsCategoryRow(
                    id = "about",
                    icon = Icons.Filled.Info,
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
 * One settings category row. Leading icon renders through
 * [SettingsCategoryIcon] (m3-expressive Phase C) — a 40-dp coloured
 * circle holding a filled glyph.
 *
 * Accent resolution follows gotcha #3 in `docs/plans/m3-expressive.md`:
 * an explicit [accent] wins; otherwise we derive from [id] via
 * `accentFor`; otherwise we fall back to [PlaybackAccent] so a row
 * without either still renders coloured rather than going monochrome.
 */
@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    id: String? = null,
    accent: CategoryAccent? = null,
) {
    val resolvedAccent = accent ?: id?.let { accentFor(it) } ?: PlaybackAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsCategoryIcon(
            icon = icon,
            accent = resolvedAccent,
            contentDescription = null,
        )
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
