# whisperboy — SOLID refactor plan (prophylactic, mostly forward-looking)

## Status: 🟡 OPEN — Whisperboy is at end-of-Phase-B with minimal source. The bulk of this plan is forward-looking discipline: patterns drawn from tonearmboy's shipped R.A through R.F refactors, applied **before** Phases C–L land, so whisperboy never accumulates the god-objects tonearmboy had to refactor away from.

_Synthesized from tonearmboy's `docs/plans/refactor-solid.md` (50 audit findings collapsed into six lettered phases, R.A through R.E shipped, R.F partial). What follows is the prospective-discipline equivalent for whisperboy — same SOLID grammar, applied earlier in the timeline._

## What this plan IS

A codification of the SOLID lessons tonearmboy paid for retroactively, expressed as **gates on every Phase C–L commit** — narrow interfaces over fat repositories, facet settings over snapshot blobs, split controllers over god-objects, file-size discipline over 1500-LOC screens, RouteScope over inline route handling. Each lettered phase below names the patterns and the whisperboy phase that applies them.

## What this plan is NOT

A list of god-objects to split. Whisperboy doesn't have any. If/when one accumulates, that's the trigger to add a new phase here with concrete sub-steps scoped to the refactor.

The flip side: if the discipline holds across Phases C–L and no god-object emerges, this plan should stay short. Tonearmboy's R.A–R.E phases each cost 1–2 days of refactor work; the equivalent effort here is **distributed in tiny increments across the feature phases that introduce the patterns**, paid as they happen.

---

## Phase R.A pattern — narrow data interfaces from day one

**Applies to:** Phase D (scanner + book/chapter/bookmark repository) and any UI phase that consumes data (E, F, H).

**Lesson from tonearmboy R.A:** Composables that took the whole `LibraryRepository` (~30 methods) for one or two Flows forced recomposition coupling, made preview/test setup heavy (real `Context` + Room for the simplest screen), and meant a change to playlist CRUD risked breaking tab renderers. Tonearmboy's R.A defined eight narrow interfaces (`TrackSource`, `AlbumSource`, `ArtistSource`, `GenreSource`, `PlaylistStore`, `CustomTabStore`, `LibraryScanner`, `MediaChangeSource`); the concrete `LibraryRepository` implements all eight; `AppGraph` exposes each separately; UI takes only what it reads.

**Apply forward:** Phase D defines the narrow interfaces *first*, before the concrete repository. Equivalent shape for whisperboy:

| Narrow interface | Methods (rough) | Consumers |
| --- | --- | --- |
| `BookSource` | `observeBooks(filter)`, `observeBook(id)`, `search(query)` | `LibraryScreen`, `PlaybackScreen` |
| `ChapterSource` | `observeChaptersForBook(id)`, `chaptersIn(bookContent)` | `PlaybackScreen` chapter list, scanner |
| `BookmarkSource` | `observeBookmarks(bookId)`, `addBookmark(...)`, `deleteBookmark(id)`, `renameBookmark(id, title)` | `BookmarkScreen`, player top app bar |
| `LibraryScanner` | `scanRoots(roots)`, `rescan()`, `observeScanState()` | onboarding, settings rescan, foreground refresh |
| `PersistedUriPermissionStore` | `observeRoots()`, `addRoot(uri, type)`, `removeRoot(uri)` | folder picker bridge, settings → library |
| `BookSettings` | `setSpeed(bookId, x)`, `setSkipSilence(bookId, on)`, `setGain(bookId, dB)` | player overflow, settings global-defaults seeding |

The concrete `LibraryRepository` (or whatever it ends up named — could be split as the impl too) implements every interface above. `AppGraph` exposes each interface separately. **No composable takes the whole repository — composables take only the narrow contract they read.**

**Sub-steps (tick when the phase that owns the file lands):**

- [x] **R.A.1** Phase D.1 defines the narrow interfaces above (or whatever subset the work-in-flight needs) in `data/` *before* defining the concrete repository. — `BookSource`, `ChapterSource`, `BookmarkSource` shipped in `data/library/` as part of D.1. `LibraryScanner` shipped in D.2 with `SafLibraryScanner` impl. `MediaAnalyzer` shipped in D.3 with `Media3MediaAnalyzer` impl. `ScanWriter` shipped in D.4 with `LibraryRepository` impl. `LibraryRescanCoordinator` shipped in D.5 with `AndroidLibraryRescanCoordinator` impl. `BookSettings` interface will land alongside Phase J's per-book settings work.
- [x] **R.A.2** Phase D's concrete repository implements all of them. `AppGraph` exposes each interface separately, marks the concrete class internal where the language allows. — `internal class LibraryRepository` shipped in D.4. Implements `BookSource`, `ChapterSource`, `BookmarkSource`, and `ScanWriter`. AppGraph holds the concrete reference privately and exposes four narrow handles (`bookSource` / `chapterSource` / `bookmarkSource` / `scanWriter`); module-external code can only consume the narrow contracts.
- [ ] **R.A.3** Phase E composables take `BookSource`, `BookmarkSource`, etc. — never the concrete repo.
- [ ] **R.A.4** Phase F's `PlaybackController` does not import `LibraryRepository` (it takes `BookSource` + `ChapterSource` + `BookSettings`).
- [ ] **R.A.5** Verify on each phase ship: `grep -rn 'LibraryRepository' app/src/main/java/com/eight87/whisperboy/ui` → empty.

**Effort:** distributed (each phase pays a few minutes). **Risk:** none — discipline-from-day-one is cheap.

---

## Phase R.B pattern — `Setting<T>` + facets, no `SettingsSnapshot`

**Applies to:** Phase K (settings).

**Lesson from tonearmboy R.B:** A 826-LOC `SettingsRepository` with 25+ keys via the hand-rolled `stringPreferencesKey + Flow + setter + snapshot field` quartet, plus a 27-field `SettingsSnapshot` projected via a `combine(...)` that every sub-page eagerly subscribed to → toggling theme recomposed the audio screen. Tonearmboy's R.B introduced a `Setting<T>(key, default, encode, decode)` value type with `flow(store)` + `set(store, value)` extensions, grouped settings into facet interfaces (`ThemeSettings`, `PlaybackSettings`, `LibrarySettings`, `MusicSourcesSettings`, `TabLayoutSettings`), and **deleted the `SettingsSnapshot` and the `combine` that built it**. Each sub-page reads its narrow `Flow<T>` directly.

**Apply forward:** When Phase K introduces settings, never build a god `SettingsRepository` in the first place.

- [ ] **R.B.1** Phase K introduces `Setting<T>` + `EnumSetting<E>` value types in `data/settings/` (or wherever fits — Phase K decides the package).
- [ ] **R.B.2** Every Phase K key declared via `Setting<T>` — no hand-rolled `stringPreferencesKey + Flow + setter` quartet.
- [ ] **R.B.3** Whisperboy facets (provisional list — Phase K refines):
  - `PlaybackSettings` — default speed, default skip-silence, default gain, rewind/forward seconds, auto-rewind seconds
  - `SleepTimerSettings` — default duration, fade-out duration, shake-to-resume on/off, auto-arm window
  - `LibrarySettings` — sort order, grid mode, filter default
  - `ThemeSettings` — theme mode, dynamic-color on/off
- [ ] **R.B.4** No `SettingsSnapshot`. Each sub-page (`SettingsPlaybackScreen(playback: PlaybackSettings)`, etc.) reads only its facet. No `combine(...)` projecting all keys into one fat data class.
- [ ] **R.B.5** UI helpers (e.g. theme picker options) live in `ui/settings/`, not in the data-layer settings repo.

**Effort:** distributed across Phase K. **Risk:** low (no DataStore migration needed; we're picking the shape from the start).

---

## Phase R.C pattern — `PlaybackController` split from day one

**Applies to:** Phase F.2 (the UI-side controller that wraps `MediaController`).

**Lesson from tonearmboy R.C:** One 882-LOC `PlaybackUiController` owned playback connection lifecycle, state projection, transport commands, queue mutation, ReplayGain re-application, sleep timer, position ticker, settings flag mirrors, and `Player.Listener` callbacks — six independent reasons to change. Composables that just wanted `state: StateFlow<PlaybackUiState>` took the whole controller including the library handle (`MiniPlayer` does not need that). Tonearmboy's R.C extracted four narrow interfaces (`NowPlayingState`, `TransportCommands`, `QueueCommands`, `ReplayGainCommands`), pulled `PlaybackStateProjector` out (cheap position-only ticks vs listener-driven queue snapshots), moved `SleepTimer` construction to `AppGraph`, and shrunk the controller to a connection-lifecycle composer at <200 LOC.

**Apply forward:** Phase F.2 defines the narrow interfaces *before* writing the controller body, splits position-ticker from event-driven projection from the start.

- [ ] **R.C.1** Phase F.2 defines in `playback/`:
  - `NowPlayingState` — `StateFlow<PlaybackUiState>` (book + chapter + position + playing + speed + skipSilence + gain)
  - `TransportCommands` — `play() / pause() / next() / prev() / seekTo(ms) / setSpeed(x) / setSkipSilence(b) / setGain(dB)`
  - `BookCommands` — `playBook(id) / playBookFromPosition(id, ms) / seekToChapter(idx)`
  - `SleepTimerCommands` — `armTimer(mode) / cancelTimer() / observeSleepTimer(): StateFlow<SleepTimerState>`
- [ ] **R.C.2** `PlaybackController` composes the four collaborators. It's not the implementation of all of them.
- [ ] **R.C.3** `PlaybackScreen` (Phase F.1) takes `NowPlayingState` + `TransportCommands` + `SleepTimerCommands`. **Does not** import `BookSource`.
- [ ] **R.C.4** A pinned now-playing bar / mini-player (when introduced — likely Phase E.6) takes `NowPlayingState` + a one-method "tap-to-open-player" callback. Nothing else.
- [ ] **R.C.5** Position ticker is a **separate emitter** from chapter / metadata projection. Position ticks at ~250ms; chapter/metadata only on `Player.Listener` events. (Tonearmboy R.C.2: don't recompute the queue on every position tick.)
- [ ] **R.C.6** `SleepTimer` instance lives in `AppGraph`, not inside the controller (mirrors tonearmboy R.C.5 — cleanest lifecycle).

**Effort:** distributed across Phase F (and a touch more in G when the sleep timer matures). **Risk:** medium — Media3 listener wiring is fragile, exactly the same surface tonearmboy R.C.7 flagged as "highest-risk verification surface in the plan." Lean on the existing `scripts/smoke-test.sh` plus an AVD pass after Phase F.5 (notification + lock-screen).

---

## Phase R.D pattern — file-size discipline + state hoisting

**Applies to:** every Phase E, F, G, H, K, L composable surface.

**Lesson from tonearmboy R.D:** `LibraryScreen.kt` reached 1528 LOC housing the chrome scaffold, five tab dispatchers (~840 LOC of near-duplicates), sort comparator factories, `TrackRow`, `MultiSelectBar`, `AlphabetScroller`, `SectionHeader`, `EmptyState`, and four pre-D.28 wrapper shims — five reasons to change. Adding a sixth content type meant editing every tab. R.D split mechanically first (no behaviour change), then collapsed the five dispatchers behind a `TabSpec<T>` strategy.

**Apply forward:** No Compose UI file ships over **500 LOC**. Split mechanically when approaching that threshold; per-row / per-overlay / per-section files. Pure-logic helpers (sort comparators, selection-state transitions, queue-reorder math) live in non-Compose files so they're unit-testable without `createComposeRule()`.

Whisperboy's library is a single cover-grid (no per-type tabs like tonearmboy's tracks/albums/artists/genres/playlists), so the **`TabSpec<T>` engine itself doesn't apply** — but the split-early discipline does, and `rememberSelectionState()` (R.D.4) DOES apply for any multi-select interaction (e.g. bulk-mark-completed in the library overflow sheet).

- [ ] **R.D.1** No Compose UI file ships over 500 LOC. Soft heuristic: at 400 LOC, look for the next split. (CLAUDE.md SOLID-S already captures this.)
- [ ] **R.D.2** Selection state for multi-select interactions hoisted into `rememberSelectionState()` — pure transitions, unit-testable without Compose.
- [ ] **R.D.3** Sort comparators live in `LibrarySorting.kt` (or analog), pure functions, no Compose.
- [ ] **R.D.4** `EmptyState`, `BookCover`, `BookRow`, `LibraryTopBar` get their own files when each crosses ~50 LOC of meaningful code.

**Effort:** distributed; usually free if you split as you write rather than at the end. **Risk:** none.

---

## Phase R.E pattern — `WhisperboyApp` shrink + RouteScope from day one

**Applies to:** the moment the nav graph grows past one entry (currently `HomeRoute` only — Phase F adds the player route, then Phase H bookmarks, K settings, L onboarding).

**Lesson from tonearmboy R.E:** `TonearmboyApp.kt` at 820 LOC had every `entry<Destination>` inline-rendering its route with full data plumbing (closed against extension), plus SAF launchers, the playlist picker overlay, the music-sources dialog, the import-collision dialog, the deeplink reactor, and four `LaunchedEffect`s mirroring settings into the controller — six unrelated change-axes. R.E defined a `RouteScope` data interface and per-destination `Register(scope)` extensions on the sealed `Destination` interface; `TonearmboyApp` shrunk to <150 LOC (theme + scaffold + a single dispatch block). Cross-cutting concerns lifted into `remember*Controller` helpers (`rememberPlaylistBackupController`, `rememberAddToPlaylistController`, `rememberPlaybackSettingsBridge`).

**Apply forward:** When `WhisperboyApp.kt` grows past ~3 inline `entry<...>` blocks, refactor to RouteScope **before** it gets fat.

- [ ] **R.E.1** Currently: `WhisperboyApp.kt` has one `entry<HomeRoute>` and is ~25 LOC. Fine until Phase F.
- [ ] **R.E.2** When Phase F adds the player route: introduce `RouteScope` interface (`graph: AppGraph`, `backStack`, `snackbar`, `playback: TransportCommands`, the relevant settings facets, callbacks).
- [ ] **R.E.3** Sealed `Destination` interface; per-destination `Destination.Register(scope: RouteScope)` extensions. One file per destination grouping (`routes/PlayerRoute.kt`, `routes/BookmarkRoute.kt`, etc.).
- [ ] **R.E.4** `WhisperboyApp.kt` stays under 150 LOC: theme + scaffold + a single `entryProvider { destination -> destination.Register(scope) }` block.
- [ ] **R.E.5** Cross-cutting controllers lifted into `remember*Controller(...)` helpers — sleep-timer-arm-from-deeplink, foreground-rescan, etc.
- [ ] **R.E.6** `PlaybackService` does not import anything from `ui/`. If it needs to construct an `Intent` to open the activity from a notification, a `SessionActivityIntentFactory` interface lives in `playback/` and `AppGraph` provides the impl. (Tonearmboy R.E.8.)

**Effort:** mostly free if applied before `WhisperboyApp` grows. **Risk:** low (`@Serializable` `Destination` keys must round-trip through `SavedStateHandle`; same caveat as tonearmboy R.E).

---

## Phase R.F — standalone wins (apply when the work touches them)

These are discrete items that don't block the lettered phases. Some apply right now (template ballast); most apply when Phase D / F / K touches the relevant code.

- [x] **R.F.1** Delete template ballast (`app/.../ui/home/HomeScreen.kt`, `HomeScreenViewModel.kt`, `data/DataRepository.kt`, the `home_*` strings) when Phase E lands the library cover grid. Cannot delete now — the activity needs *something* to render. Ticks naturally at Phase E ship. (Tonearmboy R.F.8 analog.) — **shipped earlier than expected (Phase C)**: Phase C's onboarding bridge IS the "something" the activity renders, so the template ballast was deleted in the same commit as Phase C land. `data/DataRepository.kt` removed; `ui/home/HomeScreenViewModel.kt` and the test removed; `home_greeting` and `home_error_loading` strings removed; `HomeScreen.kt` rewritten to render the library bridge directly (gates on roots, launches SAF picker, shows FolderType bottom sheet, lists configured roots with remove). Phase E will replace the bridge with the cover grid in turn.
- [x] **R.F.2** Phase D's Room schema lives in **per-entity files** (`BookEntity.kt`, `ChapterEntity.kt`, `BookmarkEntity.kt`, etc.), not a single `Entities.kt`. (Tonearmboy R.F.7 analog — preventative.) — shipped in D.1: three separate entity files + three separate DAO files in `data/library/`. No `Entities.kt`. Future entities (e.g. when Phase I adds chapter-mark tables, or future scan-state tables) follow the same pattern.
- [x] **R.F.3** Phase D's scanner uses `suspend` functions throughout. **No `runBlocking`** on the scanner path — `MediaAnalyzer.extractMetadata`, `Mp4BoxParser.parse`, etc. are all `suspend`. (Tonearmboy R.F.10 analog.) — D.1 DAOs are suspend (or Flow); D.2's `LibraryScanner.scan` is suspend, body wraps in `withContext(Dispatchers.IO)`. No `runBlocking` introduced. Phase I's M4B / Matroska parsers (`Mp4BoxParser`, `MatroskaChapter`) and Phase D.3's `MediaAnalyzer` will follow the same pattern when they land.
- [ ] **R.F.4** Domain split: `BookContent` (cache-faithful, persisted in Room) vs `ScannedBook` (scan-only superset with extra fields the cache doesn't keep — e.g. raw cover bytes before they're written to disk). Avoid silent contract drift in `Mapping.toDomain` style code. (Tonearmboy R.F.4 analog.)
- [ ] **R.F.5** `PlaybackService.onCreate` is wiring only. Extract `QueuePersistenceController` (or the whisperboy equivalent — `BookPositionPersistenceController`?) and `NotificationLayoutController` when those concerns gain weight (Phase F.7 for the notification, somewhere around Phase D for position persistence). (Tonearmboy R.F.11 analog.)
- [ ] **R.F.6** `MediaChangeObserver` analog: SAF doesn't have a `ContentObserver`, but there's an analog — *foreground rescan* and *settings-driven rescan*. Both ride one debounce policy; one helper, not two. (Tonearmboy R.F.9 analog.)
- [ ] **R.F.7** Settings catalog discipline: per-section files, not one 683-LOC catalog. Settings rows carry their own `@Composable Render(entry)`. (Tonearmboy R.F.13 + R.F.14 analog — Phase K applies them from the start.)
- [ ] **R.F.8** `rememberSettingPicker<T>(...)` helper so each settings sub-page body is a `bindings` list, not 100+ LOC of `var xPicker by remember { mutableStateOf(false) }`. (Tonearmboy R.F.17 analog.)
- [ ] **R.F.9** Detail-screen Flows on the data layer: `BookSource.observeBooksByAuthor(authorName)` etc. — UI does not filter in Compose. (Tonearmboy R.F.12 analog.)

---

## How to use this plan

- **Default work order:** R.A through R.E patterns are gates *on each Phase C–L commit that lands the relevant area*. R.F items ship standalone whenever they fit.
- **Tick checkboxes in the same commit as the work that earns them** — usually a Phase D/E/F/K commit, not a separate "refactor" commit. The discipline is invisible-when-it-works.
- **If the discipline fails and a god-object accumulates,** that's the trigger to ADD a new phase here scoped to the refactor — not unlike how tonearmboy's R.A through R.E originally appeared. This plan is allowed to grow downward.
- **Risk gates:** R.C requires manual AVD verification of every playback control surface (notification + lock-screen + position-resume on cold start). The others are mostly mechanical-with-tests.
- **When this plan is fully ticked,** restore `## Status: ✅ DONE` at the top with a re-completion note covering R.A through R.F.

## Audit provenance

This plan is **not the output of an audit on whisperboy** — whisperboy doesn't have enough source yet to need one. It's a forward-applied codification of tonearmboy's `docs/plans/refactor-solid.md`, itself synthesized from a four-agent SOLID audit run on tonearmboy commit `9388357` on 2026-05-03. If/when whisperboy accumulates a god-object, run an equivalent audit pass on the offending area and add the resulting findings as a new lettered phase.
