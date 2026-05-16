package com.eight87.whisperboy.data.coverart

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * Cursor pair threaded through [ImageSearchPagingSource].
 *
 * - [url] is the relative URL to hit on `https://duckduckgo.com/` — `/i.js` for the first
 *   page, [SearchResponse.next] for every subsequent page.
 * - [auth] is the `vqd` token returned by [CoverApi.token]. The same token is reused for
 *   every page in a single search session (Voice does the same).
 */
data class ImageSearchParams(
    val url: String,
    val auth: String,
)

/**
 * `PagingSource` that fronts the DuckDuckGo image search — cover-art.md Phase B.3.
 *
 * One instance per search query. The first `load()` call gets `params.key == null`, so we
 * lazily fetch a fresh `vqd` from [CoverApi.token]; on subsequent calls we trust the key
 * the previous page handed us. [SearchResponse.next] is `null` once DDG runs out of
 * results — that becomes `nextKey = null` and Paging stops.
 */
class ImageSearchPagingSource(
    private val coverApi: CoverApi,
    private val query: String,
) : PagingSource<ImageSearchParams, SearchResponse.ImageResult>() {

    override fun getRefreshKey(state: PagingState<ImageSearchParams, SearchResponse.ImageResult>): ImageSearchParams? =
        // A fresh search re-fetches the `vqd` token, so we never resume from a non-null
        // anchor. Returning null forces `load(key = null)` on refresh.
        null

    override suspend fun load(
        params: LoadParams<ImageSearchParams>,
    ): LoadResult<ImageSearchParams, SearchResponse.ImageResult> {
        return try {
            val current = params.key ?: freshSearchParams() ?: return LoadResult.Error(
                IllegalStateException("DuckDuckGo did not return a vqd token"),
            )
            val response = coverApi.search(query = query, vqd = current.auth, url = current.url)
            LoadResult.Page(
                data = response.results,
                prevKey = null,
                nextKey = response.next?.let { ImageSearchParams(url = it, auth = current.auth) },
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            LoadResult.Error(throwable)
        }
    }

    private suspend fun freshSearchParams(): ImageSearchParams? {
        val vqd = coverApi.token(query) ?: return null
        return ImageSearchParams(url = CoverApi.DEFAULT_SEARCH_URL, auth = vqd)
    }
}
