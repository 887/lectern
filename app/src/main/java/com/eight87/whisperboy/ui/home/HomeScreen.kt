package com.eight87.whisperboy.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import com.eight87.whisperboy.R
import com.eight87.whisperboy.data.library.BookSource
import com.eight87.whisperboy.data.library.FolderType
import com.eight87.whisperboy.data.library.LibraryRoot
import com.eight87.whisperboy.data.library.LibraryUiSettings
import com.eight87.whisperboy.data.library.PersistedUriPermissionStore
import com.eight87.whisperboy.ui.library.LibraryScreen
import kotlinx.coroutines.launch

/**
 * Entry-route shell.
 *
 * - When no roots are configured (first-run): empty-state CTA that launches the SAF picker
 *   and the FolderType bottom sheet. (Onboarding proper is Phase L; this is the interim.)
 * - When ≥1 root is configured: hands off to [LibraryScreen] (Phase E.1+) which owns the
 *   cover grid + the folder-management overflow (Add folder / Rescan / Manage folders).
 *
 * Takes narrow interfaces (R.A pattern); the concrete `LibraryRepository` and
 * `AndroidPersistedUriPermissionStore` stay in `AppGraph`.
 */
@Composable
fun HomeScreen(
    persistedUriPermissionStore: PersistedUriPermissionStore,
    bookSource: BookSource,
    libraryUiSettings: LibraryUiSettings,
    onBookTap: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val roots by persistedUriPermissionStore.observeRoots()
        .collectAsStateWithLifecycle(initialValue = emptyList<LibraryRoot>())

    if (roots.isEmpty()) {
        FirstRunPickerShell(
            persistedUriPermissionStore = persistedUriPermissionStore,
            modifier = modifier.fillMaxSize().padding(16.dp),
        )
    } else {
        LibraryScreen(
            bookSource = bookSource,
            persistedUriPermissionStore = persistedUriPermissionStore,
            libraryUiSettings = libraryUiSettings,
            onBookTap = onBookTap,
            onSettingsClick = onSettingsClick,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FirstRunPickerShell(
    persistedUriPermissionStore: PersistedUriPermissionStore,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) pendingUri = uri }

    Column(
        modifier = modifier,
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
        Button(onClick = { launcher.launch(null) }) {
            Text(stringResource(R.string.folder_picker_cta))
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
                TextButton(
                    onClick = { onSelect(type) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.dialog_cancel))
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
