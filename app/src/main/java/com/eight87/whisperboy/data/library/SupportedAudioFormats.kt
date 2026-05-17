package com.eight87.whisperboy.data.library

/**
 * What the scanner counts as an audio file. Includes the extensions Voice ships with plus
 * the ones whisperboy explicitly exercises in its B.5 codec smoke test (mp3 / m4a / m4b /
 * ogg / flac / webm).
 */
object SupportedAudioFormats {

    val extensions: Set<String> = setOf(
        "mp3", "m4a", "m4b", "aac", "3gp",
        "ogg", "oga", "opus", "flac",
        "wav",
        "mka", "mkv", "webm",
        "mp4",
    )

    /**
     * Whether the file looks like a supported audio file.
     *
     * Phase K.4 sub-screen — [disabledExtensions] (lowercase) lets the user exclude specific
     * extensions from scans via the "Scan filters" sub-page. A disabled extension is excluded
     * regardless of whether MIME-type detection or extension detection identified it.
     */
    fun isAudioFile(
        file: CachedDocumentFile,
        disabledExtensions: Set<String> = emptySet(),
    ): Boolean {
        if (!file.isFile) return false
        val name = file.name
        val ext = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase().orEmpty()
        if (ext.isNotEmpty() && ext in disabledExtensions) return false
        val type = file.type
        if (type != null && type.startsWith("audio/")) {
            // Even if MIME-type matched, honour the disabled-extension exclusion above.
            return true
        }
        if (name == null) return false
        return ext in extensions
    }
}
