package com.eight87.whisperboy.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.whisperboy.R
import com.eight87.whisperboy.data.library.FolderType
import com.eight87.whisperboy.data.library.LibraryRoot
import com.eight87.whisperboy.data.library.PersistedUriPermissionStore
import kotlinx.coroutines.launch

/**
 * Phase C bridge screen.
 *
 * - When no roots are configured: shows an empty-state CTA that launches the SAF picker.
 * - When ≥1 root is configured: shows the count + a list of roots with `Remove`, plus an
 *   `Add another folder` button.
 *
 * The "real" library cover grid lands in Phase E. This screen is the minimum needed to
 * unblock Phase D's scanner walking the picked trees.
 *
 * Takes [PersistedUriPermissionStore] (the narrow data interface — Phase R.A pattern), never
 * the concrete `AndroidPersistedUriPermissionStore` or the whole `AppGraph`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    persistedUriPermissionStore: PersistedUriPermissionStore,
    modifier: Modifier = Modifier,
) {
    val roots by persistedUriPermissionStore.observeRoots()
        .collectAsStateWithLifecycle(initialValue = emptyList<LibraryRoot>())
    val coroutineScope = rememberCoroutineScope()

    // After the picker returns, we hold the URI here while the user picks a FolderType.
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) pendingUri = uri
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (roots.isEmpty()) {
            EmptyLibraryState(onPick = { launcher.launch(null) })
        } else {
            ConfiguredLibraryState(
                roots = roots,
                onPickAnother = { launcher.launch(null) },
                onRemove = { uri ->
                    coroutineScope.launch { persistedUriPermissionStore.removeRoot(uri) }
                },
            )
        }
    }

    val pending = pendingUri
    if (pending != null) {
        FolderTypeSheet(
            onDismiss = { pendingUri = null },
            onSelect = { folderType ->
                coroutineScope.launch {
                    persistedUriPermissionStore.addRoot(pending, folderType)
                    pendingUri = null
                }
            },
        )
    }
}

@Composable
private fun EmptyLibraryState(
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.folder_picker_empty_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.folder_picker_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPick) {
            Text(stringResource(R.string.folder_picker_cta))
        }
    }
}

@Composable
private fun ConfiguredLibraryState(
    roots: List<LibraryRoot>,
    onPickAnother: () -> Unit,
    onRemove: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.folder_section_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = pluralStringResource(R.plurals.folder_count, roots.size, roots.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
            items(roots, key = { it.treeUri.toString() }) { root ->
                LibraryRootRow(root = root, onRemove = { onRemove(root.treeUri) })
                HorizontalDivider()
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onPickAnother, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.folder_picker_add_another))
        }
    }
}

@Composable
private fun LibraryRootRow(
    root: LibraryRoot,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = root.displayName, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(folderTypeTitle(root.folderType)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        TextButton(onClick = onRemove) {
            Text(stringResource(R.string.folder_remove_action))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderTypeSheet(
    onDismiss: () -> Unit,
    onSelect: (FolderType) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.folder_type_dialog_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            FolderType.allOrdered.forEach { type ->
                FolderTypeOption(type = type, onClick = { onSelect(type) })
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    }
}

@Composable
private fun FolderTypeOption(
    type: FolderType,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(folderTypeTitle(type)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(folderTypeSubtitle(type)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun folderTypeTitle(type: FolderType): Int = when (type) {
    FolderType.SingleFile -> R.string.folder_type_singlefile_title
    FolderType.SingleFolder -> R.string.folder_type_singlefolder_title
    FolderType.Root -> R.string.folder_type_root_title
    FolderType.Author -> R.string.folder_type_author_title
}

private fun folderTypeSubtitle(type: FolderType): Int = when (type) {
    FolderType.SingleFile -> R.string.folder_type_singlefile_subtitle
    FolderType.SingleFolder -> R.string.folder_type_singlefolder_subtitle
    FolderType.Root -> R.string.folder_type_root_subtitle
    FolderType.Author -> R.string.folder_type_author_subtitle
}
