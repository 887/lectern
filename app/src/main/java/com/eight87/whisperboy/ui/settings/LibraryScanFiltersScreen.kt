package com.eight87.whisperboy.ui.settings

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.whisperboy.R
import com.eight87.whisperboy.data.library.LibraryRescanCoordinator
import com.eight87.whisperboy.data.library.LibraryScanFilterSettings
import com.eight87.whisperboy.data.library.SupportedAudioFormats
import kotlinx.coroutines.launch

/**
 * Phase K.4 sub-screen — Scan filters.
 *
 * Checkbox list of the supported audio extensions in [SupportedAudioFormats.extensions].
 * Toggling a checkbox writes the new disabled-set to [LibraryScanFilterSettings] AND
 * triggers a forced rescan via [LibraryRescanCoordinator.requestRescan] so the library
 * grid reflects the change immediately. Snackbar confirms the rescan kicked.
 *
 * The persisted set holds DISABLED extensions (an extension toggled OFF lands in the
 * set); empty set = every supported extension scanned, which is the default. Storing
 * disabled-rather-than-enabled keeps new app-version extensions opt-in for existing
 * users automatically — see [LibraryScanFilterSettings] kdoc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScanFiltersScreen(
    libraryScanFilterSettings: LibraryScanFilterSettings,
    libraryRescanCoordinator: LibraryRescanCoordinator,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val rescanScheduledMessage = stringResource(R.string.settings_library_scan_filters_rescan_snackbar)

    val disabled by libraryScanFilterSettings.disabledExtensions
        .collectAsStateWithLifecycle(initialValue = emptySet())

    // Stable alphabetical extension list so the UI doesn't reshuffle on recomposition.
    val allExtensions = remember { SupportedAudioFormats.extensions.sorted() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_library_scan_filters_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.library_folders_back_cd),
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = stringResource(R.string.settings_library_scan_filters_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    allExtensions.forEach { ext ->
                        val enabled = ext !in disabled
                        CheckboxRow(
                            label = ".$ext",
                            checked = enabled,
                            onCheckedChange = { nowEnabled ->
                                val next = if (nowEnabled) disabled - ext else disabled + ext
                                scope.launch {
                                    libraryScanFilterSettings.setDisabledExtensions(next)
                                    libraryRescanCoordinator.requestRescan(force = true)
                                    snackbarHostState.showSnackbar(rescanScheduledMessage)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Checkbox,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
