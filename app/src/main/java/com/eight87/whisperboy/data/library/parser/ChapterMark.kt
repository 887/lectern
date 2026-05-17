package com.eight87.whisperboy.data.library.parser

/**
 * One chapter marker extracted from a container's embedded metadata.
 *
 * Phase I emits these from the M4B / Matroska / Vorbis parsers. The scanner converts a list
 * of marks into `ScannedChapter` rows by treating consecutive marks' position deltas as
 * durations (last chapter duration = file duration - last mark position).
 */
data class ChapterMark(
    /** Start position in milliseconds from the beginning of the file. */
    val positionMs: Long,
    /** Chapter title — may be empty if the container provides no display name. */
    val title: String,
)
