package com.eight87.whisperboy.data.coverart

import kotlinx.serialization.Serializable

/**
 * DuckDuckGo `/i.js` JSON shape (cover-art.md Phase B.2).
 *
 * The endpoint is public-but-undocumented; Voice has been shipping against it for years.
 * Only the four fields we render are declared — DDG returns many more (`source`,
 * `licence`, `title`, etc.) which we ignore via `ignoreUnknownKeys = true` on the
 * Retrofit `Json` config (see [CoverApiModule]).
 */
@Serializable
data class SearchResponse(
    /**
     * Relative URL (e.g. `/i.js?...&s=100&...`) that pages further into the same query, or
     * `null` when the result set is exhausted. [ImageSearchPagingSource] threads this as the
     * next load's URL.
     */
    val next: String? = null,
    val results: List<ImageResult> = emptyList(),
) {

    @Serializable
    data class ImageResult(
        val width: Int = 0,
        val height: Int = 0,
        /** Full-resolution image URL — fetched only when the user taps a thumbnail. */
        val image: String,
        /** DuckDuckGo-hosted thumbnail URL — what the grid actually renders. */
        val thumbnail: String,
    )
}
