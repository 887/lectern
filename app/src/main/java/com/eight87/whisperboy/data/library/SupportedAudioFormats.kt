package com.eight87.whisperboy.data.library

/**
 * What the scanner counts as an audio file. Includes the extensions Voice ships with plus
 * the ones whisperboy explicitly exercises in its B.5 codec smoke test (mp3 / m4a / m4b /
 * ogg / flac / webm).
 */
internal object SupportedAudioFormats {

    val extensions: Set<String> = setOf(
        "mp3", "m4a", "m4b", "aac", "3gp",
        "ogg", "oga", "opus", "flac",
        "wav",
        "mka", "mkv", "webm",
        "mp4",
    )

    fun isAudioFile(file: CachedDocumentFile): Boolean {
        if (!file.isFile) return false
        val type = file.type
        if (type != null && type.startsWith("audio/")) return true
        // Some providers don't fill MIME type for SAF entries — fall back to the extension.
        val name = file.name ?: return false
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return ext in extensions
    }
}
