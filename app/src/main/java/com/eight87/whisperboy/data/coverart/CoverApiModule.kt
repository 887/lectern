package com.eight87.whisperboy.data.coverart

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Composition-root helper for the cover-art HTTP stack (cover-art.md Phase B.4).
 *
 * AppGraph wires this up once at startup; [provideCoverApi] returns the single [CoverApi]
 * the whole app shares. The class is intentionally a thin object rather than a DI module —
 * whisperboy avoids Hilt/Koin per CLAUDE.md, so this stays a hand-rolled factory.
 */
object CoverApiModule {

    /**
     * Hard-coded desktop-Chrome `User-Agent`. DuckDuckGo refuses requests without one (or
     * with a default OkHttp UA — the `vqd` token is then absent from the response). Voice
     * reads its UA from a feature flag; we have no settings surface for cover-art (see
     * cover-art.md §Settings surface) so a sensible-default constant beats build-config
     * wiring.
     */
    const val USER_AGENT: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private const val BASE_URL: String = "https://duckduckgo.com/"

    /**
     * OkHttpClient with a single User-Agent interceptor. Shared between Retrofit (for the
     * paged search) and direct full-image downloads inside the picker composable, so the
     * UA is consistent on both call paths.
     */
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val withUa = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
            chain.proceed(withUa)
        }
        .build()

    fun provideCoverApi(okHttpClient: OkHttpClient): CoverApi {
        val json = Json {
            // DDG returns extra fields we don't model (source / title / licence / …) —
            // tolerate them rather than crashing the search on every shape change.
            ignoreUnknownKeys = true
            // Some optional fields show up as `null` on certain image results.
            explicitNulls = false
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            // Scalars converter parses the raw HTML body returned by the `/?q=…` endpoint
            // as a plain `String`; the kotlinx-serialization converter handles `/i.js`.
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return CoverApi(retrofit.create(InternalCoverApi::class.java))
    }
}
