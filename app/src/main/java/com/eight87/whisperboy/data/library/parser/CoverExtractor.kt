package com.eight87.whisperboy.data.library.parser

/**
 * Cover-art Phase A.3 — narrow interface for extracting an embedded cover image from a
 * single audio file. Mirrors Voice's `CoverExtractor` shape (Voice's `MatroskaCoverExtractor`
 * + the MP4 / MP3 paths inside its `BookParser`), split per container so [CoverExtractorDispatcher]
 * can route by magic bytes / MIME.
 *
 * Implementations read directly from a [SeekableSource] (an in-memory byte array in tests, or
 * a [SafSeekableDataSource] in production) — they do not own the source's lifecycle. The
 * dispatcher opens the source, picks the impl, calls [extract], and closes the source.
 *
 * Returning `null` means "no embedded cover found"; throwing means "the file's structure
 * was malformed enough that we can't tell". Callers should catch and treat both as "no
 * cover" — cover extraction is a hint, not a hard requirement.
 */
internal fun interface CoverExtractor {
    fun extract(): ByteArray?
}
