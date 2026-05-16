package com.eight87.whisperboy.data.coverart

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Raw Retrofit interface against `https://duckduckgo.com/` — cover-art.md Phase B.1.
 *
 * Internal because callers should depend on the narrow public [CoverApi] (R.A pattern —
 * narrow interfaces, concrete impls stay non-public). Two endpoints model DuckDuckGo's
 * two-step image-search protocol:
 *
 * 1. [auth] — `GET /?q=<query>` returns the rendered search-results HTML. The `vqd` token
 *    is embedded in a `vqd=...&` substring; [CoverApi.token] extracts it with a regex.
 * 2. [search] — `GET /i.js?q=<query>&vqd=<token>` returns the JSON [SearchResponse].
 *    Subsequent pages reuse the `next` URL returned by the previous page.
 *
 * Both call sites send the desktop-Chrome `User-Agent` from [CoverApiModule]; DDG rejects
 * requests without one.
 */
internal interface InternalCoverApi {

    @GET("/")
    suspend fun auth(@Query("q") search: String): String

    @GET
    suspend fun search(
        @Url url: String,
        @Query("q") query: String,
        @Query("vqd") auth: String,
    ): SearchResponse
}
