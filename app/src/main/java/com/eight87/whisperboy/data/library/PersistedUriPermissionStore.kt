package com.eight87.whisperboy.data.library

import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * Narrow data interface (per `docs/plans/refactor-solid.md` Phase R.A pattern) for the set of
 * audiobook-library tree URIs the user has opted into.
 *
 * The store is responsible for:
 * - Holding [Intent.FLAG_GRANT_READ_URI_PERMISSION] persistably across app restarts
 *   (via `ContentResolver.takePersistableUriPermission` / `releasePersistableUriPermission`).
 * - Persisting the user's [FolderType] choice for each picked tree.
 * - Exposing the current set as a [Flow] so the UI can react to add / remove without polling.
 *
 * Composables should depend on this interface, not on the concrete Android implementation.
 */
interface PersistedUriPermissionStore {

    fun observeRoots(): Flow<List<LibraryRoot>>

    suspend fun addRoot(treeUri: Uri, folderType: FolderType)

    suspend fun removeRoot(treeUri: Uri)
}
