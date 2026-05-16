package com.eight87.whisperboy.data.coverart

/**
 * Narrow public surface (R.A pattern) over the DuckDuckGo image-search endpoint.
 *
 * Voice's `features/cover/api/CoverApi.kt` is the spiritual analog. The two-step protocol
 * is locked in cover-art.md §The doctrine:
 *
 * 1. [token] — `GET /?q=<query>` returns HTML; the `vqd=([\d-]+)&` regex extracts the auth
 *    token. Returns `null` if the regex does not match (DDG changed shape — handle as a
 *    fresh "Error" state in the UI).
 * 2. [search] — pass the token plus the search query to load a page of results. Subsequent
 *    pages call [search] again with the previous page's [SearchResponse.next] as the [url].
 *
 * Both calls suspend on the IO dispatcher (Retrofit's coroutine adapter does the right
 * thing internally). The class is open to the package; consumers ([ImageSearchPagingSource])
 * hold one shared instance from [com.eight87.whisperboy.AppGraph].
 */
class CoverApi internal constructor(
    private val internalApi: InternalCoverApi,
) {

    /**
     * Step 1: ask DuckDuckGo for a `vqd` token tied to [query]. Parsed out of the search-
     * results HTML via [VQD_REGEX]. Voice has shipped against the same regex for years.
     */
    suspend fun token(query: String): String? {
        val html = internalApi.auth(query)
        return VQD_REGEX.find(html)?.groupValues?.getOrNull(1)
    }

    /**
     * Step 2: page through results. First call uses [DEFAULT_SEARCH_URL] + a fresh [token];
     * subsequent calls use `previousResponse.next` as [url], threading the cursor that
     * DuckDuckGo embeds in the response.
     */
    suspend fun search(
        query: String,
        vqd: String,
        url: String = DEFAULT_SEARCH_URL,
    ): SearchResponse = internalApi.search(url = url, query = query, auth = vqd)

    companion object {
        /** Relative URL on the `https://duckduckgo.com/` host for the first results page. */
        const val DEFAULT_SEARCH_URL: String = "/i.js"

        /**
         * `vqd=<token>&` lives in the HTML body of the search page. Voice uses the same
         * pattern (`features/cover/api/CoverApi.kt`); the leading `vqd=` and trailing `&`
         * anchor it against the JS variable assignments and URL query strings DDG embeds
         * the token in.
         */
        private val VQD_REGEX = Regex("vqd=([\\d-]+)&")
    }
}
