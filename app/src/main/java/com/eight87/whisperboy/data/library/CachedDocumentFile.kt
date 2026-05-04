package com.eight87.whisperboy.data.library

import androidx.documentfile.provider.DocumentFile

/**
 * In-process cache around a [DocumentFile]. SAF round-trips are notoriously slow (every metadata
 * call is a content-provider IPC) and the scanner walks deep trees — caching `name`, `length`,
 * `lastModified`, `isDirectory`, `isFile`, `type`, and `listFiles()` results turns a multi-second
 * walk into a sub-second one for trees up to a few hundred files.
 *
 * Voice analog: `voice.core.documentfile.CachedDocumentFile`. **Cache is per-instance, not global** —
 * mutate the underlying tree (add/remove a file outside the app) and you must construct a fresh
 * [CachedDocumentFile] to pick the change up. Phase D's scanner does exactly that on rescan.
 *
 * Children are themselves [CachedDocumentFile] instances — the cache extends through the tree.
 */
class CachedDocumentFile(private val raw: DocumentFile) {

    val uri get() = raw.uri

    val name: String? by lazy { raw.name }

    val length: Long by lazy { raw.length() }

    val lastModified: Long by lazy { raw.lastModified() }

    val isDirectory: Boolean by lazy { raw.isDirectory }

    val isFile: Boolean by lazy { raw.isFile }

    val type: String? by lazy { raw.type }

    val children: List<CachedDocumentFile> by lazy {
        raw.listFiles().map(::CachedDocumentFile)
    }
}
