# whisperboy — cover-art roadmap

## Status: 🟡 PLANNED — mirror Voice's approach almost exactly

Voice's cover-art module is the one piece of Voice the user explicitly reports has "never disappointed" (alongside two weeks of dropped libraries — i.e. high signal-to-noise on this specific subsystem). Mirror it.

**Voice analog (source-of-truth, browse-only — do not vendor):**

- `core/scanner/src/main/kotlin/voice/core/scanner/CoverScanner.kt` — local-first scan
- `core/scanner/src/main/kotlin/voice/core/scanner/matroska/MatroskaCoverExtractor.kt` — Matroska/MKV embedded
- `core/scanner/src/main/kotlin/voice/core/scanner/BookParser.kt` — MP4/MP3 embedded
- `features/cover/src/main/kotlin/voice/features/cover/` — the whole "select cover from internet" UI module
  - `api/CoverApi.kt` + `InternalCoverApi.kt` + `CoverModule.kt` — the DuckDuckGo client
  - `api/ImageSearchPagingSource.kt` + `SearchResponse.kt` — paged results
  - `SelectCoverFromInternet.kt` + `CoverContents.kt` — staggered grid UI
  - `crop/CropTransformation.kt` + `crop/CropOverlay.kt` + `crop/EditCoverDialog.kt` — crop step

License: Voice is **GPLv3**. We do not copy code. We re-implement the same shapes in our own files under MIT.

## The doctrine (locked, drawn directly from Voice)

1. **Local-first, in strict order, automatic.** No user opt-in needed:
   1. If the book already has a saved cover file on disk → done
   2. Walk the book's SAF directory via `DocumentFile.listFiles()`. **First file with `type?.startsWith("image/") == true` wins.** Copy bytes into our covers dir. No preference order — Voice doesn't bother with `cover.jpg` > `folder.jpg` priority, first image found wins.
   3. Otherwise, extract embedded cover from the **first 5 chapters** (not just chapter 1 — chapter 1 sometimes lacks one). First success wins.
2. **No automated network lookups, ever.** Voice ships zero background web requests for cover art. Period. The only time a network round-trip happens is when the user explicitly opens "Search online" for a specific book.
3. **One service: DuckDuckGo image search.** Voice hits `https://duckduckgo.com/` with a two-step protocol:
   - `GET /?q=<query>` returns HTML; extract `vqd=([\d-]+)&` via regex — that's the auth token.
   - `GET /i.js?q=<query>&vqd=<token>` returns `{ next: String?, results: List<{ width, height, image, thumbnail }> }`.
   - `next` field threads a `PagingSource` for infinite scroll.
   - Send a custom `User-Agent` header (Voice reads it from a feature flag — we hard-code a sensible one or expose it as a build-config field).
4. **Per-book user flow:** book context menu → "Search online" → opens a screen with a search bar pre-filled with `<book title> <author>` → paged `LazyVerticalStaggeredGrid` of thumbnails (`StaggeredGridCells.Adaptive(minSize = 150.dp)`) → tap → crop overlay → confirm → bytes saved as that book's cover.
5. **Crop step.** After picking a result, show a `CropOverlay` over the full-res image. User adjusts the square crop, confirms, the cropped bytes get written. (Voice's `EditCoverDialog` flow.)
6. **No match-score thresholds, no service pickers, no bulk-scan workers, no per-tile load-state badges.** Three view states: `Loading` (centered `CircularProgressIndicator`), `Error` (message + Retry button), `Content` (the paged grid). That's it.
7. **Placeholder glyph for books without a cover.** Voice ships `core/ui/src/main/res/drawable/album_art.xml`. We ship our own equivalent (proposal: `Icons.Filled.MenuBook` in `surfaceContainer` background with `onSurface` tint at avatar scale, matching the M3E surface ladder).

## Phase A — local-first scan (no network)

Goal: every cover the user already has on disk renders correctly, automatically, with zero settings.

- [ ] **A.1** `CoverSaver` — owns `app/files/covers/<bookId>.jpg`. Methods: `newBookCoverFile(): File`, `setBookCover(file: File, bookId: BookId)`, `coverFor(bookId): File?`.
- [ ] **A.2** `CoverScanner` (mirror of Voice's `CoverScanner.kt`) — `suspend fun scan(books: List<Book>)`. For each book:
  - If `book.coverPath` exists on disk → skip
  - Try `findAndSaveCoverFromDisc(book)` — `DocumentFile.fromTreeUri(book.id.toUri())`, `.listFiles()`, first `image/*` MIME type wins, copy bytes via `contentResolver.openInputStream`
  - Otherwise `scanForEmbeddedCover(book)` — iterate `book.chapters.take(5)`, call `coverExtractor.extractCover(chapterUri, outputFile)`, first success wins
- [ ] **A.3** `CoverExtractor` interface; two impls:
  - `Mp4CoverExtractor` — reads the M4B `udta.meta.ilst.covr` atom + MP3 `APIC` frame (Phase I's MP4 box parser will already be walking this tree; reuse it). For MP3, `MetadataRetriever` or a tiny ID3v2 parser.
  - `MatroskaCoverExtractor` — walks EBML, finds `Attachments\AttachedFile` with `image/*` MIME, writes bytes. (Voice analog: `MatroskaCoverExtractor.kt`.)
- [ ] **A.4** Wire `CoverScanner.scan(...)` into Phase D's scanner pass — after `LibraryRepository` commits a batch of `BookEntity` rows, call `coverScanner.scan(newBooks)`. Ticks `BookEntity.coverPath` to the on-disk path.
- [ ] **A.5** `CoverArt` composable — Coil-based, takes a `BookId` (resolves `coverPath` via `CoverSaver`). Placeholder is the M3E book glyph. **Decode-size discipline**: tiles request `Size(coverSizePx, coverSizePx)`, full-screen player requests `Size.ORIGINAL` only at the player surface (tonearmboy `8d8c1a4` "Balanced load speed" lesson).
- [ ] **A.6** Per-book overflow action "Use custom cover from device" — `OPEN_DOCUMENT` for `image/*`, copy bytes via `CoverSaver`, mark with a flag (`coverSource = Custom`) so a later rescan doesn't overwrite the user's pick.

## Phase B — user-initiated DuckDuckGo search

Goal: the per-book "Search online" flow, exactly Voice's shape.

- [ ] **B.1** `data/coverart/CoverApi.kt` + `InternalCoverApi.kt` — Retrofit interface against `https://duckduckgo.com/`. Two endpoints: `auth(@Query("q") String): String` (raw HTML, parsed for `vqd=([\d-]+)&`), `search(@Url String, @Query("q") String, @Query("vqd") String): SearchResponse`.
- [ ] **B.2** `SearchResponse` data class with `next: String?` + `results: List<ImageResult { width, height, image, thumbnail }>`. `@Serializable` via `kotlinx.serialization`.
- [ ] **B.3** `ImageSearchPagingSource` — `androidx.paging` `PagingSource<ImageSearchParams, ImageResult>`. `freshSearchParams()` grabs a new `vqd` token; subsequent `load(params)` threads the `next` URL.
- [ ] **B.4** `OkHttpClient` with a `User-Agent` interceptor. Hard-coded UA (or read from `BuildConfig`); no settings exposure.
- [ ] **B.5** `SelectCoverFromInternet(bookId)` composable — three states: `Loading` (`CircularProgressIndicator`), `Error` (text + Retry button), `Content` (the staggered grid). Search bar pre-fills `<book title> <author>` on first open. Top app bar has a close button.
- [ ] **B.6** `LazyVerticalStaggeredGrid` with `StaggeredGridCells.Adaptive(minSize = 150.dp)`. `AsyncImage` per cell with `model = item.thumbnail`, `aspectRatio = item.width / item.height`, `placeholder = ColorPainter(onSurfaceVariant)`. Tap → emit `CoverClick(item)`.
- [ ] **B.7** Navigate from the library long-press sheet ("Search online") and from the player overflow ("Change cover").

## Phase C — crop step

Goal: after the user picks a search result, they crop it.

- [ ] **C.1** `EditCoverDialog` — full-screen surface that loads `item.image` (the full-res URL, not the thumbnail) via Coil, overlays a `CropOverlay` (1:1 square frame, draggable + pinch-zoom).
- [ ] **C.2** `CropTransformation` — Coil `Transformation` that applies the user's crop rect on confirm and writes the result to `CoverSaver.newBookCoverFile()`.
- [ ] **C.3** Confirm path: `coverSaver.setBookCover(croppedFile, bookId)` → invalidate Coil's memory + disk cache for this `bookId` → pop back to the caller.

## Phase D — refresh + invalidation

- [ ] **D.1** `CoverArtRepository.refresh(bookId)` — drops Coil's cache key for that bookId, re-issues. Surfaces in the long-press sheet as "Refresh cover" (forces a fresh local-first scan, useful when the user just dropped a `cover.jpg` next to the book file).
- [ ] **D.2** Manual cover (Phase A.6) and search-online cover (Phase B+C) both write through `CoverSaver.setBookCover` so the invalidation path is the same.

## Settings surface

**None.** Voice doesn't expose cover-art settings, and we don't either. The "Search online" affordance is per-book in the action sheet — no global enable / service picker / threshold slider. A user who never wants network round-trips simply never taps "Search online"; nothing else triggers a request.

(This deletes the speculative `K.7 — Cover art section` from earlier drafts of `main.md`. See K.6 below for the only residual settings touchpoint, which is unrelated.)

## Cross-references

- **Phase D scanner** in `main.md` is where local-first scan (this plan's Phase A) wires in
- **Phase E library grid** in `main.md` mounts the first consumer of `CoverArt`; the long-press sheet adds "Search online" (this plan's Phase B entry point)
- **Phase F player** in `main.md` uses `CoverArt` at full size
- **Phase I chapter parsing** in `main.md` already walks MP4 atoms — `Mp4CoverExtractor` reuses the visitor pattern
- **`refactor-solid.md` R.A** — `CoverScanner` / `CoverExtractor` / `CoverApi` are narrow interfaces from day one
- **`m3-expressive.md`** — the placeholder glyph + the staggered grid surface use the M3E surface ladder

## Risk / unknowns

- **DuckDuckGo `vqd` token is undocumented.** It's a public-but-undocumented protocol. Voice has been shipping against it for years without breakage, but it's not an SLA'd API. If it breaks, we follow Voice — Voice's commit history is the early warning. (As of writing, Voice's `CoverApi.kt` uses the same regex `"vqd=([\\d-]+)&"`.)
- **No "from disk" preference order on multi-image folders.** Voice's `findAndSaveCoverFromDisc` takes the literal first `image/*` returned by `DocumentFile.listFiles()`. If a folder has `back.jpg` listed before `cover.jpg`, Voice grabs `back.jpg`. The user can always "Use custom cover from device" to fix it. We mirror — do not pre-build a preference order that wasn't asked for.
- **Coil 3 cache invalidation on the same `bookId`.** Coil keys cache on the model (the file path); when we overwrite the same file at the same path, the in-memory cache still serves the stale bitmap. Phase D.1 / C.3 explicitly drop the cache; verify this on the AVD when D.1 lands.
