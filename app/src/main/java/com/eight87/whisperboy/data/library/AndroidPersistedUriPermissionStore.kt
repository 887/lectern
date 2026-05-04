package com.eight87.whisperboy.data.library

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Android implementation of [PersistedUriPermissionStore].
 *
 * Combines two sources of truth:
 * - `ContentResolver.persistedUriPermissions` — the OS-held permission grants.
 * - [DataStore] — the user's [FolderType] choice + cached display name per URI.
 *
 * On [addRoot]: take persistable read permission via [ContentResolver.takePersistableUriPermission],
 * resolve the display name via [DocumentFile.fromTreeUri], persist `(treeUri → FolderType, name)`.
 *
 * On [removeRoot]: release the persistable permission, drop the entries from [DataStore].
 */
class AndroidPersistedUriPermissionStore(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
) : PersistedUriPermissionStore {

    override fun observeRoots(): Flow<List<LibraryRoot>> =
        dataStore.data.map { prefs ->
            val uris = prefs[KEY_URIS] ?: emptySet()
            uris.mapNotNull { uriString ->
                val typeId = prefs[stringPreferencesKey("$uriString::type")] ?: return@mapNotNull null
                val type = FolderType.fromId(typeId) ?: return@mapNotNull null
                val name = prefs[stringPreferencesKey("$uriString::name")] ?: uriString
                LibraryRoot(Uri.parse(uriString), type, name)
            }
        }

    override suspend fun addRoot(treeUri: Uri, folderType: FolderType) {
        withContext(Dispatchers.IO) {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            val displayName = resolveDisplayName(treeUri)
            val uriString = treeUri.toString()
            dataStore.edit { prefs ->
                val existing = prefs[KEY_URIS] ?: emptySet()
                prefs[KEY_URIS] = existing + uriString
                prefs[stringPreferencesKey("$uriString::type")] = FolderType.id(folderType)
                prefs[stringPreferencesKey("$uriString::name")] = displayName
            }
        }
    }

    override suspend fun removeRoot(treeUri: Uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val uriString = treeUri.toString()
            dataStore.edit { prefs ->
                val existing = prefs[KEY_URIS] ?: emptySet()
                prefs[KEY_URIS] = existing - uriString
                prefs.remove(stringPreferencesKey("$uriString::type"))
                prefs.remove(stringPreferencesKey("$uriString::name"))
            }
        }
    }

    private fun resolveDisplayName(treeUri: Uri): String =
        DocumentFile.fromTreeUri(context, treeUri)?.name
            ?: treeUri.lastPathSegment?.substringAfterLast('/')
            ?: treeUri.toString()

    private companion object {
        val KEY_URIS = stringSetPreferencesKey("library_root_uris")
    }
}
