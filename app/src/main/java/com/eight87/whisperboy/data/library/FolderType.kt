package com.eight87.whisperboy.data.library

/**
 * How the user has organised audiobooks within a single picked tree URI.
 *
 * Voice analog: `voice.core.data.api.FolderType`. The four variants cover the cases the user
 * asks the picker about — see `## Phase C` in `docs/plans/main.md`.
 */
sealed interface FolderType {

    /** One audio file = one book. The picked URI is itself the file (rare, but supported). */
    object SingleFile : FolderType

    /** One folder, multiple audio files = one book. Chapters are the files in alphabetical order. */
    object SingleFolder : FolderType

    /** Each first-level subfolder is a separate book. Chapters are the audio files inside. */
    object Root : FolderType

    /** First-level subfolders are authors; second-level subfolders are books. */
    object Author : FolderType

    companion object {
        /** Stable string id used for persistence (DataStore keys, future Room schema). */
        fun id(type: FolderType): String = when (type) {
            SingleFile -> "single_file"
            SingleFolder -> "single_folder"
            Root -> "root"
            Author -> "author"
        }

        fun fromId(id: String): FolderType? = when (id) {
            "single_file" -> SingleFile
            "single_folder" -> SingleFolder
            "root" -> Root
            "author" -> Author
            else -> null
        }

        val allOrdered: List<FolderType> = listOf(SingleFolder, Root, Author, SingleFile)
    }
}
