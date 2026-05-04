package com.eight87.whisperboy.data.library

import android.net.Uri

/**
 * One folder the user has opted into via the SAF picker, paired with the [FolderType] they
 * declared for it. The set of [LibraryRoot]s is the canonical input to Phase D's scanner.
 */
data class LibraryRoot(
    val treeUri: Uri,
    val folderType: FolderType,
    val displayName: String,
)
