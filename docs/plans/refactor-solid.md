# whisperboy — SOLID refactor plan (prophylactic, mostly forward-looking)

## Status: 🟡 PARTIAL — 1 rule still open (R.F.9, dormant). R.A complete (5/5). **R.B complete (5/5) — every facet now migrated to `Setting<T>` / `EnumSetting<E>`: `LibraryUiSettings`, `ThemeSettings`, `OnboardingSettings`, `LibraryScanFilterSettings`, `PlaybackSettings`. `SleepTimerSettings` partially migrated (boolean `shakeToResume` via `Setting<T>`; `Duration` knobs and the `autoArmWindow` LocalTime-pair stay hand-rolled — persisted vs domain type differ / atomic two-key write — documented inline).** R.C complete (6/6). **R.D complete (4/4) — `LibraryScreen.kt` (260 LOC) and `PlaybackScreen.kt` (343 LOC) are now orchestrators only; every row / tile / sheet / banner / top bar lives in its own sibling file under `ui/library/` and `ui/playback/`.** **R.E complete (6/6) — shipped: `WhisperboyApp.kt` shrunk 317 → 122 LOC, `RouteScope` + five per-grouping `routes/*Destinations.kt` files now hold every `entry<...>` block.** R.F partial — F.1/2/3/4/5/6/7/8/10 shipped, F.9 remains (no per-author detail flows yet).

_Synthesized from tonearmboy's `docs/plans/refactor-solid.md` (50 audit findings collapsed into six lettered phases, R.A through R.E shipped, R.F partial). What follows is the prospective-discipline equivalent for whisperboy — same SOLID grammar, applied earlier in the timeline._

## What this plan IS

A codification of the SOLID lessons tonearmboy paid for retroactively, expressed as **gates on every Phase C–L commit** — narrow interfaces over fat repositories, facet settings over snapshot blobs, split controllers over god-objects, file-size discipline over 1500-LOC screens, RouteScope over inline route handling. Each lettered phase below names the patterns and the whisperboy phase that applies them.

## What this plan is NOT

A list of god-objects to split. Whisperboy doesn't have any. If/when one accumulates, that's the trigger to add a new phase here with concrete sub-steps scoped to the refactor.

The flip side: if the discipline holds across Phases C–L and no god-object emerges, this plan should stay short. Tonearmboy's R.A–R.E phases each cost 1–2 days of refactor work; the equivalent effort here is **distributed in tiny increments across the feature phases that introduce the patterns**, paid as they happen.

---

> **Status of the tonearmboy precedent (2026-05-11):** R.A through R.E are all shipped and validated in tonearmboy as of `4e2ff73`. The patterns below stop being speculative — they're the locked answer. R.D.2 (`rememberSelectionState()`) ships in tonearmboy `40da803` (selection across every library tab). R.C.4 (mini-player takes only `NowPlayingState`) ships in tonearmboy `3436ae2` (lazy-mount NowPlaying behind sheet). Refer to those commits as the shipped reference for whisperboy's equivalent work.

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
- [x] **R.A.3** Phase E composables take `BookSource`, `BookmarkSource`, etc. — never the concrete repo. — _shipped: `grep -rn 'import.*LibraryRepository' app/src/main/java/com/eight87/whisperboy/ui` returns 0 matches; only doc-comment mentions in `CoverArtRefresher.kt:24` and `BookmarkScreen.kt:78`._
- [x] **R.A.4** Phase F's `PlaybackController` does not import `LibraryRepository` (it takes `BookSource` + `ChapterSource` + `BookSettings`). — _shipped: `playback/PlaybackController.kt:15-17` imports `BookSource` and `ChapterSource`; no `LibraryRepository` import. Constructor (line 135) takes the narrow interfaces._
- [x] **R.A.5** Verify on each phase ship: `grep -rn 'LibraryRepository' app/src/main/java/com/eight87/whisperboy/ui` → empty. — _shipped: only matches are docstring references in `CoverArtRefresher.kt` and `BookmarkScreen.kt`, no imports or usages._

**Effort:** distributed (each phase pays a few minutes). **Risk:** none — discipline-from-day-one is cheap.

---

## Phase R.B pattern — `Setting<T>` + facets, no `SettingsSnapshot` — R.B.2 full-facet migration shipped in commit `074eb4f`

**Applies to:** Phase K (settings).

**Lesson from tonearmboy R.B:** A 826-LOC `SettingsRepository` with 25+ keys via the hand-rolled `stringPreferencesKey + Flow + setter + snapshot field` quartet, plus a 27-field `SettingsSnapshot` projected via a `combine(...)` that every sub-page eagerly subscribed to → toggling theme recomposed the audio screen. Tonearmboy's R.B introduced a `Setting<T>(key, default, encode, decode)` value type with `flow(store)` + `set(store, value)` extensions, grouped settings into facet interfaces (`ThemeSettings`, `PlaybackSettings`, `LibrarySettings`, `MusicSourcesSettings`, `TabLayoutSettings`), and **deleted the `SettingsSnapshot` and the `combine` that built it**. Each sub-page reads its narrow `Flow<T>` directly.

**Apply forward:** When Phase K introduces settings, never build a god `SettingsRepository` in the first place.

- [x] **R.B.1** Phase K introduces `Setting<T>` + `EnumSetting<E>` value types in `data/settings/` (or wherever fits — Phase K decides the package). — _shipped: `data/settings/Setting.kt` defines `class Setting<T>(flow, setter)` + `class EnumSetting<E : Enum<E>>` + `DataStore<Preferences>.setting(key, default)` / `enumSetting(name, default)` factory helpers. KDoc documents the per-facet migration path._
- [x] **R.B.2** Every Phase K key declared via `Setting<T>` — no hand-rolled `stringPreferencesKey + Flow + setter` quartet. — _shipped: `LibraryUiSettings` (reference impl, `EnumSetting`s), `ThemeSettings` (`EnumSetting` + `Setting<Boolean>`), `OnboardingSettings` (`Setting<Boolean>`), `LibraryScanFilterSettings` (`Setting<Set<String>>` via new `stringSetSetting` factory with lowercase normalisation on read AND write), `PlaybackSettings` (every knob via new `setting(key, decode, encode)` overload — seek-seconds coerce-to-set, speed/gain clamp-to-range live next to the key declaration), `SleepTimerSettings.shakeToResume` (`Setting<Boolean>`). The two `Duration` knobs and the `autoArmWindow` LocalTime-pair stay hand-rolled with an inline comment — persisted `Long ms` / `minute-of-day` differs from domain `Duration` / `LocalTime?`, and the auto-arm setter atomically writes a pair of keys; both shapes fall outside the uniform-T `Setting<T>` contract. `Setting.kt` gained two factory overloads to absorb the validation knobs: `setting(key, decode, encode)` for validated scalars and `stringSetSetting(name, normalise)` for normalised string sets._
- [x] **R.B.3** Whisperboy facets (provisional list — Phase K refines):
  - `PlaybackSettings` — default speed, default skip-silence, default gain, rewind/forward seconds, auto-rewind seconds
  - `SleepTimerSettings` — default duration, fade-out duration, shake-to-resume on/off, auto-arm window
  - `LibrarySettings` — sort order, grid mode, filter default
  - `ThemeSettings` — theme mode, dynamic-color on/off
  — _shipped: `data/playback/PlaybackSettings.kt`, `data/playback/SleepTimerSettings.kt`, `data/library/LibraryUiSettings.kt` + `LibraryScanFilterSettings.kt`, `data/theme/ThemeSettings.kt` — each a narrow facet interface with DataStore-backed impl._
- [x] **R.B.4** No `SettingsSnapshot`. Each sub-page (`SettingsPlaybackScreen(playback: PlaybackSettings)`, etc.) reads only its facet. No `combine(...)` projecting all keys into one fat data class. — _shipped: `grep -rn 'SettingsSnapshot' app/src/main/java/com/eight87/whisperboy/` returns 0 matches. Sub-pages take their facet directly._
- [x] **R.B.5** UI helpers (e.g. theme picker options) live in `ui/settings/`, not in the data-layer settings repo. — _shipped: `ui/settings/` has 12 files (picker dialogs, category icon, easter-egg, screens); `data/*/Settings.kt` files are pure data-layer with no Compose imports._

**Effort:** distributed across Phase K. **Risk:** low (no DataStore migration needed; we're picking the shape from the start).

---

## Phase R.C pattern — `PlaybackController` split from day one

**Applies to:** Phase F.2 (the UI-side controller that wraps `MediaController`).

**Lesson from tonearmboy R.C:** One 882-LOC `PlaybackUiController` owned playback connection lifecycle, state projection, transport commands, queue mutation, ReplayGain re-application, sleep timer, position ticker, settings flag mirrors, and `Player.Listener` callbacks — six independent reasons to change. Composables that just wanted `state: StateFlow<PlaybackUiState>` took the whole controller including the library handle (`MiniPlayer` does not need that). Tonearmboy's R.C extracted four narrow interfaces (`NowPlayingState`, `TransportCommands`, `QueueCommands`, `ReplayGainCommands`), pulled `PlaybackStateProjector` out (cheap position-only ticks vs listener-driven queue snapshots), moved `SleepTimer` construction to `AppGraph`, and shrunk the controller to a connection-lifecycle composer at <200 LOC.

**Apply forward:** Phase F.2 defines the narrow interfaces *before* writing the controller body, splits position-ticker from event-driven projection from the start.

- [x] **R.C.1** Phase F.2 defines in `playback/`:
  - `NowPlayingState` — `StateFlow<PlaybackUiState>` (book + chapter + position + playing + speed + skipSilence + gain)
  - `TransportCommands` — `play() / pause() / next() / prev() / seekTo(ms) / setSpeed(x) / setSkipSilence(b) / setGain(dB)`
  - `BookCommands` — `playBook(id) / playBookFromPosition(id, ms) / seekToChapter(idx)`
  - `SleepTimerCommands` — `armTimer(mode) / cancelTimer() / observeSleepTimer(): StateFlow<SleepTimerState>`
  — _shipped: `playback/PlaybackController.kt:45/57/75/113` declares all four interfaces._
- [x] **R.C.2** `PlaybackController` composes the four collaborators. It's not the implementation of all of them. — _shipped: `playback/PlaybackController.kt:141` — `internal class PlaybackController(...) : NowPlayingState, TransportCommands, BookCommands, PlayerHandle` — note `SleepTimerCommands` is NOT mixed in (lives separately as `AndroidSleepTimer` in AppGraph)._
- [x] **R.C.3** `PlaybackScreen` (Phase F.1) takes `NowPlayingState` + `TransportCommands` + `SleepTimerCommands`. **Does not** import `BookSource`. — _shipped: `grep -n 'BookSource' app/src/main/java/com/eight87/whisperboy/ui/playback/PlaybackScreen.kt` returns only a doc-comment at line 88 ("no import of `BookSource`"). `BookmarkSource` + `ChapterSource` are imported but not `BookSource`._
- [x] **R.C.4** A pinned now-playing bar / mini-player (when introduced — likely Phase E.6) takes `NowPlayingState` + a one-method "tap-to-open-player" callback. Nothing else. — _shipped: `ui/library/NowPlayingBar.kt:76-78` — `fun NowPlayingBar(nowPlayingState: NowPlayingState, transport: TransportCommands, ...)`. No `BookSource` or fat-controller import._
- [x] **R.C.5** Position ticker is a **separate emitter** from chapter / metadata projection. Position ticks at ~250ms; chapter/metadata only on `Player.Listener` events. (Tonearmboy R.C.2: don't recompute the queue on every position tick.) — _shipped: `PlaybackController.kt:131-163` documents and implements split — position ticker is decoupled from `playerSnapshot` (listener-driven projection)._
- [x] **R.C.6** `SleepTimer` instance lives in `AppGraph`, not inside the controller (mirrors tonearmboy R.C.5 — cleanest lifecycle). — _shipped: `AppGraph.kt:323` constructs `AndroidSleepTimer` at graph level, exposed as `SleepTimerCommands`._

**Effort:** distributed across Phase F (and a touch more in G when the sleep timer matures). **Risk:** medium — Media3 listener wiring is fragile, exactly the same surface tonearmboy R.C.7 flagged as "highest-risk verification surface in the plan." Lean on the existing `scripts/smoke-test.sh` plus an AVD pass after Phase F.5 (notification + lock-screen).

---

## Phase R.D pattern — file-size discipline + state hoisting

**Applies to:** every Phase E, F, G, H, K, L composable surface.

**Lesson from tonearmboy R.D:** `LibraryScreen.kt` reached 1528 LOC housing the chrome scaffold, five tab dispatchers (~840 LOC of near-duplicates), sort comparator factories, `TrackRow`, `MultiSelectBar`, `AlphabetScroller`, `SectionHeader`, `EmptyState`, and four pre-D.28 wrapper shims — five reasons to change. Adding a sixth content type meant editing every tab. R.D split mechanically first (no behaviour change), then collapsed the five dispatchers behind a `TabSpec<T>` strategy.

**Apply forward:** No Compose UI file ships over **500 LOC**. Split mechanically when approaching that threshold; per-row / per-overlay / per-section files. Pure-logic helpers (sort comparators, selection-state transitions, queue-reorder math) live in non-Compose files so they're unit-testable without `createComposeRule()`.

Whisperboy's library is a single cover-grid (no per-type tabs like tonearmboy's tracks/albums/artists/genres/playlists), so the **`TabSpec<T>` engine itself doesn't apply** — but the split-early discipline does, and `rememberSelectionState()` (R.D.4) DOES apply for any multi-select interaction (e.g. bulk-mark-completed in the library overflow sheet).

- [x] **R.D.1** No Compose UI file ships over 500 LOC. Soft heuristic: at 400 LOC, look for the next split. (CLAUDE.md SOLID-S already captures this.) — _shipped in commit `0a3dd71`: `ui/library/LibraryScreen.kt` split 931 → 260 LOC (orchestrator only); `ui/playback/PlaybackScreen.kt` split 943 → 343 LOC. Extracted siblings in `ui/library/`: `LibraryTopBar.kt`, `LibraryContent.kt`, `BookGridTile.kt`, `BookListRow.kt`, `LibraryEmptyState.kt`, `LibrarySectionHeader.kt`, `LibraryHealthBanner.kt`, `BookActionSheet.kt`, `ManageFoldersFolderTypeSheet.kt`; in `ui/playback/`: `PlaybackTopBar.kt`, `PlaybackCover.kt`, `PlaybackScrubber.kt`, `PlaybackTransport.kt`, `ChapterQueue.kt`, `ChapterQueueRow.kt`, `PlaybackOptionsSheet.kt`._
- [x] **R.D.2** Selection state for multi-select interactions hoisted into `rememberSelectionState()` — pure transitions, unit-testable without Compose. — _n/a today: `grep -rn 'multiSelect\|MultiSelect\|rememberSelectionState' app/src/main/java/com/eight87/whisperboy/` returns 0 matches. No bulk-mark-completed multi-select UI shipped through Phase P; rule reactivates when one is added._
- [x] **R.D.3** Sort comparators live in `LibrarySorting.kt` (or analog), pure functions, no Compose. — _shipped: `ui/library/LibrarySorting.kt` exists, pure helpers (no `@Composable`), imports only data types._
- [x] **R.D.4** `EmptyState`, `BookCover`, `BookRow`, `LibraryTopBar` get their own files when each crosses ~50 LOC of meaningful code. — _shipped in commit `0a3dd71`: every analog now lives in its own sibling file. Library side: `LibraryEmptyState.kt`, `BookGridTile.kt` (BookCover analog), `BookListRow.kt`, `LibrarySectionHeader.kt`, `LibraryHealthBanner.kt`, `BookActionSheet.kt`, `LibraryTopBar.kt` (hosts `LibrarySearchBar` too), `ManageFoldersFolderTypeSheet.kt`. Player side: `PlaybackTopBar.kt`, `PlaybackCover.kt`, `PlaybackTransport.kt`, `PlaybackScrubber.kt`, `ChapterQueue.kt`, `ChapterQueueRow.kt`, `PlaybackOptionsSheet.kt`. Each extracted composable marked `internal` (package-private)._

**Effort:** distributed; usually free if you split as you write rather than at the end. **Risk:** none.

---

## Phase R.E pattern — `WhisperboyApp` shrink + RouteScope from day one — shipped in commit `5513541` (R.E.2–R.E.5)

**Applies to:** the moment the nav graph grows past one entry (currently `HomeRoute` only — Phase F adds the player route, then Phase H bookmarks, K settings, L onboarding).

**Lesson from tonearmboy R.E:** `TonearmboyApp.kt` at 820 LOC had every `entry<Destination>` inline-rendering its route with full data plumbing (closed against extension), plus SAF launchers, the playlist picker overlay, the music-sources dialog, the import-collision dialog, the deeplink reactor, and four `LaunchedEffect`s mirroring settings into the controller — six unrelated change-axes. R.E defined a `RouteScope` data interface and per-destination `Register(scope)` extensions on the sealed `Destination` interface; `TonearmboyApp` shrunk to <150 LOC (theme + scaffold + a single dispatch block). Cross-cutting concerns lifted into `remember*Controller` helpers (`rememberPlaylistBackupController`, `rememberAddToPlaylistController`, `rememberPlaybackSettingsBridge`).

**Apply forward:** When `WhisperboyApp.kt` grows past ~3 inline `entry<...>` blocks, refactor to RouteScope **before** it gets fat.

- [x] **R.E.1** Currently: `WhisperboyApp.kt` has one `entry<HomeRoute>` and is ~25 LOC. Fine until Phase F. — _shipped: rule was a statement-of-current-state; Phase F has since landed and `WhisperboyApp.kt` grew, so this observation is satisfied as written._
- [x] **R.E.2** When Phase F adds the player route: introduce `RouteScope` interface (`graph: AppGraph`, `backStack`, `snackbar`, `playback: TransportCommands`, the relevant settings facets, callbacks). — _shipped in commit `5513541`: `ui/routes/RouteScope.kt` holds `graph: AppGraph`, `backStack: NavBackStack<NavKey>`, `coroutineScope`, `snackbar: SnackbarHostState`, plus the cross-cutting callbacks (`openSheet`, `finishOnboarding`). Each per-destination registration takes a `RouteScope` and pulls only what it needs out of `graph`._
- [x] **R.E.3** Sealed `Destination` interface; per-destination `Destination.Register(scope: RouteScope)` extensions. One file per destination grouping (`routes/PlayerRoute.kt`, `routes/BookmarkRoute.kt`, etc.). — _shipped in commit `5513541`: `ui/routes/Destination.kt` declares the sealed marker + a `registerAllDestinations(scope)` dispatcher. Five grouping files: `OnboardingDestinations.kt`, `HomeDestination.kt`, `LibraryDestinations.kt`, `PlaybackDestinations.kt` (Bookmark + CoverSearch — `PlaybackRoute` deliberately NOT registered, sheet overlay only), `SettingsDestinations.kt`. Each file exposes an `EntryProviderScope<NavKey>.registerXxxEntries(scope)` extension; entries are registered against `@Serializable NavKey` keys (kept as source of truth for back-stack persistence)._
- [x] **R.E.4** `WhisperboyApp.kt` stays under 150 LOC: theme + scaffold + a single `entryProvider { destination -> destination.Register(scope) }` block. — _shipped in commit `5513541`: `WhisperboyApp.kt` is 122 LOC. Body is: onboarding-gated initial key, back stack, sheet `Animatable`, `BackHandler`, mini-player bottom-pad, `RouteScope` construction, and one `entryProvider { registerAllDestinations(routeScope) }` call. `NowPlayingSheet` overlay remains at root (it is not a route)._
- [x] **R.E.5** Cross-cutting controllers lifted into `remember*Controller(...)` helpers — sleep-timer-arm-from-deeplink, foreground-rescan, etc. — _shipped in commit `5513541` as a no-op: no cross-cutting controllers exist in whisperboy yet (no sleep-timer-arm-from-deeplink, no foreground-rescan controller; `LibraryRescanCoordinator` lives in `AppGraph` and is consumed directly). When the first one lands (likely a future deeplink reactor parallel to tonearmboy's `pendingDeeplink`), introduce a `rememberDeeplinkController(routeScope)` helper alongside `RouteScope` rather than inlining into `WhisperboyApp`._
- [x] **R.E.6** `PlaybackService` does not import anything from `ui/`. If it needs to construct an `Intent` to open the activity from a notification, a `SessionActivityIntentFactory` interface lives in `playback/` and `AppGraph` provides the impl. (Tonearmboy R.E.8.) — _shipped: `grep '^import' app/src/main/java/com/eight87/whisperboy/playback/PlaybackService.kt` — zero imports from `ui/`. Only platform, R, WhisperboyApplication, and Media3._

**Effort:** mostly free if applied before `WhisperboyApp` grows. **Risk:** low (`@Serializable` `Destination` keys must round-trip through `SavedStateHandle`; same caveat as tonearmboy R.E).

---

## Phase R.F — standalone wins (apply when the work touches them)

These are discrete items that don't block the lettered phases. Some apply right now (template ballast); most apply when Phase D / F / K touches the relevant code.

- [x] **R.F.1** Delete template ballast (`app/.../ui/home/HomeScreen.kt`, `HomeScreenViewModel.kt`, `data/DataRepository.kt`, the `home_*` strings) when Phase E lands the library cover grid. Cannot delete now — the activity needs *something* to render. Ticks naturally at Phase E ship. (Tonearmboy R.F.8 analog.) — **shipped earlier than expected (Phase C)**: Phase C's onboarding bridge IS the "something" the activity renders, so the template ballast was deleted in the same commit as Phase C land. `data/DataRepository.kt` removed; `ui/home/HomeScreenViewModel.kt` and the test removed; `home_greeting` and `home_error_loading` strings removed; `HomeScreen.kt` rewritten to render the library bridge directly (gates on roots, launches SAF picker, shows FolderType bottom sheet, lists configured roots with remove). Phase E will replace the bridge with the cover grid in turn.
- [x] **R.F.2** Phase D's Room schema lives in **per-entity files** (`BookEntity.kt`, `ChapterEntity.kt`, `BookmarkEntity.kt`, etc.), not a single `Entities.kt`. (Tonearmboy R.F.7 analog — preventative.) — shipped in D.1: three separate entity files + three separate DAO files in `data/library/`. No `Entities.kt`. Future entities (e.g. when Phase I adds chapter-mark tables, or future scan-state tables) follow the same pattern.
- [x] **R.F.3** Phase D's scanner uses `suspend` functions throughout. **No `runBlocking`** on the scanner path — `MediaAnalyzer.extractMetadata`, `Mp4BoxParser.parse`, etc. are all `suspend`. (Tonearmboy R.F.10 analog.) — D.1 DAOs are suspend (or Flow); D.2's `LibraryScanner.scan` is suspend, body wraps in `withContext(Dispatchers.IO)`. No `runBlocking` introduced. Phase I's M4B / Matroska parsers (`Mp4BoxParser`, `MatroskaChapter`) and Phase D.3's `MediaAnalyzer` will follow the same pattern when they land.
- [x] **R.F.4** Domain split: `BookContent` (cache-faithful, persisted in Room) vs `ScannedBook` (scan-only superset with extra fields the cache doesn't keep — e.g. raw cover bytes before they're written to disk). Avoid silent contract drift in `Mapping.toDomain` style code. (Tonearmboy R.F.4 analog.) — _shipped: `data/library/ScanSnapshot.kt:25` defines `data class ScannedBook` separate from `data/library/BookEntity.kt:25`'s Room entity; doc explicitly references R.F.4._
- [x] **R.F.5** `PlaybackService.onCreate` is wiring only. Extract `QueuePersistenceController` (or the whisperboy equivalent — `BookPositionPersistenceController`?) and `NotificationLayoutController` when those concerns gain weight (Phase F.7 for the notification, somewhere around Phase D for position persistence). (Tonearmboy R.F.11 analog.) — _shipped: `PlaybackService.kt` is 110 LOC; `onCreate` just creates the notification channel and sets `DefaultMediaNotificationProvider`. Position persistence lives in `PlaybackController`, not the service. No `NotificationLayoutController` needed at current weight._
- [x] **R.F.6** `MediaChangeObserver` analog: SAF doesn't have a `ContentObserver`, but there's an analog — *foreground rescan* and *settings-driven rescan*. Both ride one debounce policy; one helper, not two. (Tonearmboy R.F.9 analog.) — _shipped: `data/library/LibraryRescanCoordinator.kt` interface + `AndroidLibraryRescanCoordinator.kt` impl provides a single helper for foreground/settings-triggered rescans._
- [x] **R.F.7** Settings catalog discipline: per-section files, not one 683-LOC catalog. Settings rows carry their own `@Composable Render(entry)`. (Tonearmboy R.F.13 + R.F.14 analog — Phase K applies them from the start.) — _shipped: `ui/settings/` has 12 separate per-section files (`PlaybackSettingsScreen`, `SleepTimerSettingsScreen`, `ThemeSettingsScreen`, `LibrarySettingsScreen`, `LibraryGridModeDefaultScreen`, `LibraryScanFiltersScreen`, `LibrarySortDefaultScreen`, `AboutScreen`, `LicensesScreen`, `SettingsScreen`, etc.); largest is 472 LOC._
- [x] **R.F.8** `rememberSettingPicker<T>(...)` helper so each settings sub-page body is a `bindings` list, not 100+ LOC of `var xPicker by remember { mutableStateOf(false) }`. (Tonearmboy R.F.17 analog.) — _shipped: `ui/settings/SettingPicker.kt` adds `rememberSettingPicker<T>(setting, current)` returning a `SettingPickerState<T>` with `visible` / `show()` / `select(value)` / `dismiss()`, plus a generic `RadioPickerDialog<T>(...)` for the common enum-radio case. Existing sub-pages are not rewired in this commit — the helper is additive; migrate each sub-page (`PlaybackSettingsScreen`, `SleepTimerSettingsScreen`, etc.) as it's touched next._
- [ ] **R.F.9** Detail-screen Flows on the data layer: `BookSource.observeBooksByAuthor(authorName)` etc. — UI does not filter in Compose. (Tonearmboy R.F.12 analog.) — _open: no detail-by-author / detail-by-narrator screens have shipped yet; rule reactivates when those land. Currently `BookSource` has `observeBooks(filter)` taking a `BookFilter` value; no per-attribute query methods exist or are required._
- [x] **R.F.10** `CoverScanner` / `CoverExtractor` / `CoverApi` / `CoverSaver` as narrow interfaces from day one (see [`cover-art.md`](cover-art.md)). Mirrors Voice's split: `CoverScanner` orchestrates local-first scan; `CoverExtractor` is a sealed interface with `Mp4CoverExtractor` + `MatroskaCoverExtractor` impls; `CoverApi` wraps the DuckDuckGo client (auth + paged search); `CoverSaver` owns the on-disk covers dir. UI takes only what it reads — the library grid takes `CoverSaver.coverFor(bookId)`, the search screen takes `CoverApi` via the paging source. — _shipped: `data/library/parser/CoverExtractor.kt` (+ `Mp3`/`Mp4`/`Matroska` impls + `CoverExtractorDispatcher`), `data/library/FolderCoverFinder.kt`, `data/library/CoverSource.kt`/`CoverStore.kt`, `data/coverart/CoverApi.kt` (+ `InternalCoverApi`/`ImageSearchPagingSource`). UI consumes the narrow contracts (`ui/common/CoverArt.kt`, `ui/coverart/SelectCoverFromInternet.kt`)._

---

## How to use this plan

- **Default work order:** R.A through R.E patterns are gates *on each Phase C–L commit that lands the relevant area*. R.F items ship standalone whenever they fit.
- **Tick checkboxes in the same commit as the work that earns them** — usually a Phase D/E/F/K commit, not a separate "refactor" commit. The discipline is invisible-when-it-works.
- **If the discipline fails and a god-object accumulates,** that's the trigger to ADD a new phase here scoped to the refactor — not unlike how tonearmboy's R.A through R.E originally appeared. This plan is allowed to grow downward.
- **Risk gates:** R.C requires manual AVD verification of every playback control surface (notification + lock-screen + position-resume on cold start). The others are mostly mechanical-with-tests.
- **When this plan is fully ticked,** restore `## Status: ✅ DONE` at the top with a re-completion note covering R.A through R.F.

## Audit provenance

This plan is **not the output of an audit on whisperboy** — whisperboy doesn't have enough source yet to need one. It's a forward-applied codification of tonearmboy's `docs/plans/refactor-solid.md`, itself synthesized from a four-agent SOLID audit run on tonearmboy commit `9388357` on 2026-05-03. If/when whisperboy accumulates a god-object, run an equivalent audit pass on the offending area and add the resulting findings as a new lettered phase.
