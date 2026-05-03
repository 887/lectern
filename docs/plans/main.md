# lectern — main build plan

## Status: 🟡 PRE-PHASE 0 (skeleton + plans only)

_Repo skeleton, README, CLAUDE.md, and this plan landed in the initial commit. No code yet. Phase 0 is the first phase that actually does anything._

## Stack (locked)

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Audio:** androidx.media3 (ExoPlayer + MediaSession + **`MediaLibraryService`** — see CLAUDE.md for why `MediaLibraryService` rather than `MediaSessionService`)
- **Data:** Room (book / chapter / bookmark cache), DataStore (preferences), SAF / `DocumentFile` (audiobook files — no MediaStore, no `READ_EXTERNAL_STORAGE`)
- **Build front-end:** Google's [Android CLI](https://developer.android.com/tools/agents/android-cli) (`android` command, launched April 2026). Wraps Gradle, SDK, install, run. **No Android Studio.**
- **Build back-end:** Gradle (driven via the Android CLI). The repo includes a Gradle wrapper.
- **Unit tests:** Robolectric (JVM-only, zero device).
- **UI tests:** [mobile-mcp](https://github.com/mobile-next/mobile-mcp) over ADB. Current target: headless AVD `medium_phone` (Android 16 / API 36, RSS ~3.2 GB) — see Phase 0.
- **Knowledge:** `android docs search` for live Android API guidance. `android-skills-mcp` for the official Android skills inside Claude Code.

## Spiritual sibling

[Voice](https://github.com/PaulWoitaschek/Voice) (GPLv3, by Paul Woitaschek) is the closest functional analog. Lectern is **not** a fork — written from scratch in Kotlin + Compose, MIT-licensed — but Voice's mapped design space is the one we start in:

- SAF-only library, four folder modes (`SingleFile` / `SingleFolder` / `Root` / `Author`)
- Per-book speed, skip-silence, gain stored on the `BookContent` row
- Sleep timer with fade-out, shake-to-resume, end-of-chapter mode, auto-bookmark on fire
- Custom MP4 box parser for M4B chapter markers
- `MediaLibraryService` for Android Auto

When a phase below has a Voice analog, the phase header references the Voice module that solved that problem so a subagent can study prior art before inventing.

---

## Phase 0 — prerequisites (one-time, on the host)

Goal: a buildable host. These run once per developer machine. Tracked here so the environment is verifiable before agents go to work. (Same machine as tonearm, so most of these are no-ops on this user's box, but we tick them for completeness on a fresh checkout.)

- [ ] **0.1** Install Google's Android CLI: `curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/android -o ~/.local/bin/android && chmod +x ~/.local/bin/android`. The launcher self-bootstraps a 78 MB runtime on first invocation, including a bundled JDK 21 at `~/.android/cli/bundles/<hash>/jre/`. Verify with `android --version`.
- [ ] **0.2** `android sdk install platforms/android-34 build-tools/34.0.0` — installs to `~/Android/Sdk/`. Bump version when AGP requires.
- [ ] **0.3** JDK 21 bundled by the Android CLI is sufficient for AGP 9. System Java only matters if a subagent invokes `./gradlew` directly without going through `android` — set `JAVA_HOME` to a system JDK 17+ in that case (see CLAUDE.md).
- [ ] **0.4** `mobile` MCP server registered at **project scope** (`lectern/.mcp.json`) — already committed in the initial skeleton; verify with `claude mcp list` from inside the repo.
- [ ] **0.5** `android-skills` MCP server registered at **project scope** (`lectern/.mcp.json`) — already committed in the initial skeleton; verify with `claude mcp list` from inside the repo.
- [ ] **0.6** Test target: **headless AVD `medium_phone`** (shared with tonearm). Created via `android emulator create --profile=medium_phone`. Started headlessly. Visible to ADB as `emulator-5554`.

---

## Phase A — scaffold

Goal: a buildable, sideload-able APK that boots into a blank Compose screen. Everything that follows assumes this exists. Mirror tonearm Phase A almost exactly — same template choice, same gradle setup, same package convention.

- [ ] **A.0** Browse `android create list` and pick the closest official template. Default expectation: `empty-activity` (the same template tonearm used).
- [ ] **A.1** `android create --name=lectern --output=. <template>` from inside the repo root. Verify the generated layout: `app/`, `gradle/wrapper/`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`. Rename package from `com.example.lectern` to `com.eight87.lectern`. Rename theme to `LecternTheme`.
- [ ] **A.2** Add Media3 deps to `app/build.gradle.kts`: `media3-exoplayer`, `media3-session`, `media3-ui`. Pin via a single `media3 = "1.10.0"` key in `libs.versions.toml` shared by all three module entries (no Maven BOM exists for Media3).
- [ ] **A.3** `AndroidManifest.xml` adds: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`. **No `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`** — lectern is SAF-only. `minSdk = 28` (matching Voice's floor; SAF flake on older APIs makes the cost of supporting 26-27 not worth the user count). `compileSdk` and `targetSdk` set to current stable.
- [ ] **A.4** `.gitignore` covers `build/`, `.gradle/`, `local.properties`, `captures/`, `.cxx/`, `*.apk`, `*.aab`, `keystore.properties`, `*.keystore`, `*.jks`, plus `.idea/`, `*.iml`. (Already committed in skeleton — verify.)
- [ ] **A.5** Build verification: `./gradlew assembleDebug` succeeds. APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **A.6** Install verification: `android run --apks=app/build/outputs/apk/debug/app-debug.apk` launches the placeholder activity on `emulator-5554`. Confirm via `dumpsys window | grep mCurrentFocus`.

---

## Phase B — core playback

Goal: ExoPlayer plays a known audio file. `MediaLibrarySession` is registered. Audio focus is honored. **Voice analog:** `:core:playback` (`PlaybackService`, `VoicePlayer`, `LibrarySessionCallback`).

- [ ] **B.1** `PlayerHolder` wraps an `ExoPlayer` instance. Build with `setAudioAttributes(..., handleAudioFocus = true)`, `setHandleAudioBecomingNoisy(true)`, `setWakeMode(WAKE_MODE_LOCAL)`. Use a **`OnlyAudioRenderersFactory`** (audio-only `RenderersFactory` — strips video renderers entirely; lifted as a *pattern* from Voice, not vendored) to keep APK small and avoid any video decoder surface.
- [ ] **B.2** `PlaybackService : MediaLibraryService` declared in the manifest with `foregroundServiceType="mediaPlayback"` and the `androidx.media3.session.MediaLibraryService` intent filter. Stub notification (replaced for real in Phase F).
- [ ] **B.3** `MediaLibrarySession` wired to the Player via `MediaLibrarySession.Builder(this, player, callback)`. Stub `MediaLibrarySession.Callback` returns an empty browse tree — real tree lands in Phase N.
- [ ] **B.4** Audio focus delegated to ExoPlayer's built-in handling via `setAudioAttributes(..., handleAudioFocus = true)`. (Same pattern tonearm validated; verified against `kb://android/media/media3/session/background-playback`.)
- [ ] **B.5** Format smoke test on the real target: play one each of MP3, M4B (with embedded chapters), OGG Vorbis, FLAC, WebM/Matroska. Tonearm's smoke pattern (`scripts/smoke-test.sh`, broadcast intent → service plays via `/data/local/tmp` fixtures) ports cleanly to lectern. The M4B + Matroska format coverage is new; both pass at the codec layer (Phase I parses the chapter markers — Phase B just needs the audio to decode).

---

## Phase C — SAF folder picker + persisted URI permissions

Goal: the user can pick one or more folders, lectern persists `Uri.persistableUriPermission` across reboots, and the picked tree is reachable as a `DocumentFile`. **Voice analog:** `:features:folderPicker`, `PersistedUriPermissions`.

- [ ] **C.1** Onboarding entry — first launch shows a "Pick your audiobook folder" empty state with a single CTA. (Real onboarding flow lands in Phase L; this is the placeholder bridge.)
- [ ] **C.2** `Intent.ACTION_OPEN_DOCUMENT_TREE` launched via `ActivityResultContracts.OpenDocumentTree`. On success, take persistable read permission via `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`.
- [ ] **C.3** `PersistedUriPermissions` repo — list, add, remove. Backed by `contentResolver.persistedUriPermissions`. Surfaces a `Flow<List<Uri>>` of currently-held trees.
- [ ] **C.4** `FolderType` sealed type with the four Voice variants (`SingleFile`, `SingleFolder`, `Root`, `Author`). Each picked tree gets a `FolderType` (default = `Root`, picker shows the four options as a follow-up bottom sheet on add).
- [ ] **C.5** `CachedDocumentFile` wrapper around `DocumentFile` (caches `name` / `length` / `lastModified` / `isDirectory` / `listFiles` results in-process — SAF round-trips are otherwise miserable on large trees, especially on SD card). **Voice analog:** `:core:documentfile`.
- [ ] **C.6** Settings → Library page lists current roots with their `FolderType` + remove button (revokes the persistable permission). Real Settings UX lands in Phase K; this is the minimum to unblock multi-root testing in Phase D.

---

## Phase D — scanner: walk SAF tree, classify, persist

Goal: walk every root, classify folders by `FolderType`, materialize Book / Chapter rows in Room, drop dropped books to `inactive` (soft-delete) on rescan. **Voice analog:** `:core:scanner` (`MediaScanner`, `BookParser`, `ChapterParser`, `MediaAnalyzer`).

- [ ] **D.1** Room schema baseline: `BookEntity` (book id = SHA-256 of root tree URI + relative path; speed, skipSilence, gain, name, author, cover, currentChapterIndex, positionInChapter, lastPlayedAt, active), `ChapterEntity` (book id, index, name, duration, file uri or position-within-file, list of `ChapterMark`s), `BookmarkEntity` (uuid, book id, chapter id, title, time, addedAt, setBySleepTimer). Schema export on; Room version 1; no `fallbackToDestructiveMigration`.
- [ ] **D.2** `Scanner` walks each root via `CachedDocumentFile`, dispatches by `FolderType`:
  - `SingleFile` → one `Book`, chapters from embedded markers (Phase I) or one chapter == whole file
  - `SingleFolder` → one `Book`, chapters = audio files in alphabetical order (`BookComparator`)
  - `Root` → each first-level subfolder is a separate `Book`
  - `Author` → first-level subfolders are authors, second-level are `Book`s
- [ ] **D.3** `MediaAnalyzer` extracts duration + title/author/cover via `MediaMetadataRetriever` for each audio file. Cover bytes: prefer embedded → fallback to `cover.jpg` / `folder.jpg` next to the audio file.
- [ ] **D.4** Diff & apply: scanner produces a `ScanSnapshot`; `LibraryRepository.applyScan(snapshot)` writes only changed rows in a single Room transaction, marks gone-but-was-here books `active = false` (soft-delete preserves bookmarks + position if the user re-adds the folder).
- [ ] **D.5** Rescan triggers: manual (Settings → Rescan), on app foreground (debounced 30s), on persistable URI permission added/removed. **No `ContentObserver`** — SAF doesn't surface change events the same way `MediaStore` does; we rescan on signal, not on observation.
- [ ] **D.6** `library-smoke-test.sh` — fetches two synthetic test books (one `SingleFolder` 3-chapter book, one `SingleFile` M4B with embedded chapters), pushes to `/sdcard/Audiobooks/lectern-test/`, points the SAF picker at it via the canonical onboarding flow, asserts both books land in Room with the right chapter counts.

---

## Phase E — book overview UI (the library screen)

Goal: cover-grid library screen, three filter chips (Current / Not started / Completed), sort menu, search bar, long-press → bottom sheet (rename, change cover, mark completed, delete book record). **Voice analog:** `:features:bookOverview`.

- [ ] **E.1** `LibraryScreen` composable — cover-forward grid, default columns auto-sized to width, list-toggle in the top app bar (`GridMode` sealed type). Each cover renders the book title underneath + a thin progress bar overlay along the bottom edge of the cover (% of total duration listened).
- [ ] **E.2** Filter chips row: Current / Not started / Completed. Filter is a sealed `BookFilter`; query plumbed through `BookSource.observeBooks(filter)`.
- [ ] **E.3** Sort menu (Recent, Title, Author). Persisted in DataStore.
- [ ] **E.4** Search — full-text over book title + author. Room FTS4 if it composes cleanly with the `BookEntity` shape, else `LIKE` fallback. Same pattern tonearm validated in C.4.
- [ ] **E.5** Long-press → `BookActionSheet` (Rename, Change cover from device, Mark completed, Mark not started, Forget book record). "Forget" only deletes the row + bookmarks — does not delete files (lectern doesn't delete audiobook files; the user's library is precious).
- [ ] **E.6** Now-playing bar pinned to the bottom when something is playing — book title, chapter title, play/pause, taps through to the player screen (Phase F).

---

## Phase F — playback screen (now-playing)

Goal: full-screen player. **Voice analog:** `:features:playbackScreen`.

- [ ] **F.1** `PlaybackScreen` composable — large centered square cover dominates the top half; book title + chapter title stacked below; scrubber with current-position / chapter-duration timestamps; transport row (prev-chapter, rewind-N-seconds, play/pause, forward-N-seconds, next-chapter); top app bar with back, sleep-timer button, bookmark button, overflow.
- [ ] **F.2** `PlaybackController` (UI-side wrapper around `MediaController`) exposes `StateFlow<PlaybackUiState>` (book, chapter, position, playing, speed, skipSilence, gain). UI consumes via `collectAsStateWithLifecycle`. Position ticker runs at ~250ms while playing, off when paused.
- [ ] **F.3** Configurable rewind / forward seconds — defaults 30s back / 30s forward; long-press on either button opens a quick selector. Persisted in DataStore. (Voice 25.12.1 added this — common request.)
- [ ] **F.4** Auto-rewind on resume — when resuming after >5 minutes paused, rewind by `autoRewindSeconds` (configurable, default 5s). Implemented in `PlaybackController.onResume`.
- [ ] **F.5** Chapter list — bottom sheet from the player overflow. Lists chapters with per-chapter progress, current chapter highlighted, tap → seek to chapter start.
- [ ] **F.6** Background gradient tinted by the cover-art's extracted Palette swatch (Voice does this; mild but adds polish).
- [ ] **F.7** Notification + lock-screen via `MediaStyle` notification, channel `lectern_playback` (IMPORTANCE_LOW, silent). Replaces Phase B's stub. Verified via `dumpsys media_session` and lock-screen screenshots.

---

## Phase G — sleep timer

Goal: the best sleep timer in class. **Voice analog:** `:core:sleeptimer:impl` (`SleepTimerImpl`, `IsTimeInRange`, `ShakeDetector`, `CreateBookmarkAtCurrentPosition`).

- [ ] **G.1** `SleepTimerService` — modes are sealed `SleepTimerMode`: `Timed(Duration)`, `EndOfChapter`. Default duration persisted in DataStore (`SleepTimerPreference`).
- [ ] **G.2** Sleep timer bottom sheet from the player top-app-bar button. Quick-pick: 5 / 10 / 15 / 30 / 60 minutes + "End of chapter" + custom. Active timer shows remaining time on the button itself (filled state).
- [ ] **G.3** Fade-out: when remaining < `fadeOutDuration` (default 30s), poll every 200ms and ramp `Player.volume` down with a `FastOutSlowInInterpolator`-equivalent curve. After pause, restore volume to 1.0f for next session.
- [ ] **G.4** Shake-to-resume: after the timer pauses, register a `ShakeDetector` (`SensorManager.SENSOR_ACCELEROMETER`) for 30s. On detected shake, resume + restart the timer with the user's last-used duration. Window is tight to avoid false-positives from putting the phone down.
- [ ] **G.5** Auto-bookmark on fire: when the timer pauses playback, write a `Bookmark` with `setBySleepTimer = true`, title = "Sleep timer", time = current position. Surfaces in the bookmark list with a clock icon to distinguish from manual.
- [ ] **G.6** Auto-enable in daily window: settings option to auto-arm the timer between two configurable times (e.g. 22:00–02:00). When in window AND playback starts AND no timer active, arm with the default duration. (`IsTimeInRange` is the unit.)

---

## Phase H — bookmarks

Goal: named bookmarks tied to position-in-book. **Voice analog:** `:features:bookmark`.

- [ ] **H.1** `BookmarkScreen` — list of bookmarks for the current book, grouped by chapter. Each row shows title, time, addedAt, "set by sleep timer" badge if applicable.
- [ ] **H.2** Add bookmark via the player top-app-bar bookmark button. Default title = chapter title + position; user can rename inline.
- [ ] **H.3** Tap → seek to bookmark position + auto-resume.
- [ ] **H.4** Long-press → rename / delete.

---

## Phase I — chapter parsing (M4B + Matroska)

Goal: parse embedded chapter markers from M4B (Apple chap-track + Nero chpl atoms) and Matroska/MKV/WebM (EBML chapters). Read chapters via SAF stream — no temp files. **Voice analog:** `:core:scanner` (`Mp4BoxParser` + visitors `ChapVisitor`/`ChplVisitor`/`MdhdVisitor`/`StcoVisitor`/`StscVisitor`/`SttsVisitor`; `MatroskaChapter`, `MatroskaMetaDataExtractor`, `SafSeekableDataSource`).

This is *the* feature that separates a real audiobook player from a music player pointed at long files. Plan it carefully; budget time.

- [ ] **I.1** `SafSeekableDataSource` — random-access reader over a SAF `Uri` (Media3-style `DataSource`-flavored, but for our parser, not for ExoPlayer). Wraps `ContentResolver.openAssetFileDescriptor` and `seek` on the underlying `FileDescriptor`. Necessary because SAF streams are forward-only by default.
- [ ] **I.2** `Mp4BoxParser` — visitor-based MP4 atom walker. Visit `moov.trak.mdia.minf.stbl.{stco,stsc,stsz,stts}` to build offset tables; visit `moov.udta.chpl` for Nero chapter list; visit `chap` track-reference + corresponding text track for Apple chap-track.
- [ ] **I.3** Apple chap-track decoder — for each chunk in the chap text track, decode `(start_time, length, title_string)`. Convert sample-time to seconds via `mdhd` timescale.
- [ ] **I.4** Nero chpl decoder — straight `(timestamp_in_100ns, title)` list. Convert to seconds.
- [ ] **I.5** `MatroskaChapter` parser — walk EBML, find `\Chapters\EditionEntry\ChapterAtom`, extract `ChapterTimeStart` (ns) + `ChapterDisplay\ChapString`. Skip ordered / nested-edition variants for v1.
- [ ] **I.6** Vorbis comment chapter extractor — `CHAPTER001`, `CHAPTER001NAME`, etc. Less common but cheap to add.
- [ ] **I.7** `ChapterParser` dispatch — by extension + sniffed magic, route to the right parser. Output: `List<ChapterMark>(positionMs, title)`. Falls back to "one mark at 0 with the file name" if nothing parses.
- [ ] **I.8** Wire into Phase D's scanner — `SingleFile` books with parsed chapters get rich `Chapter` rows; without, get one chapter == whole file.

---

## Phase J — speed, skip silence, volume gain

Goal: per-book speed (0.5x–3.5x), skip silence, volume gain in dB. **Voice analog:** `VoicePlayer` + `VolumeGain` audio processor.

- [ ] **J.1** Speed — ExoPlayer's native `setPlaybackSpeed(Float)` (Sonic time-stretching, no FFmpeg). Persisted on `BookEntity`. Settings UX: slider + numeric display + "reset to 1.0x" button. Range 0.5–3.5 in 0.05 steps.
- [ ] **J.2** Skip silence — `Player.skipSilenceEnabled = enabled`. Persisted on `BookEntity`. Toggle in player overflow.
- [ ] **J.3** Volume gain — custom `VolumeGain` `AudioProcessor` measured in `Decibel` (range -3 dB to +12 dB), applied as a pre-amp in the audio pipeline before output. Persisted on `BookEntity`. Settings UX: slider with numeric dB readout. Note: this is *additive* to the system volume, not a substitute.
- [ ] **J.4** Per-book vs global defaults — when a book is first scanned it inherits global defaults (DataStore); subsequent edits write to the `BookEntity` row, not back to the defaults. This is Voice's behaviour and the right one — a quiet book gets +6 dB without the next book screaming.

---

## Phase K — settings screen

Goal: full settings tree. **Voice analog:** `:features:settings`.

- [ ] **K.1** `SettingsScreen` — Material 3 settings list. Sections: Playback, Sleep timer, Library, Theme, About.
- [ ] **K.2** Playback section: default speed, default skip silence, default gain, rewind/forward seconds, auto-rewind seconds, equalizer launcher (`AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL` system intent — same pattern as Voice).
- [ ] **K.3** Sleep timer section: default duration, fade-out duration, shake-to-resume on/off, auto-arm window.
- [ ] **K.4** Library section: list of roots with `FolderType` + remove + add-new; manual rescan button.
- [ ] **K.5** Theme section: light / dark / follow-system; dynamic color toggle (Material You).
- [ ] **K.6** About section: version, build hash, license, link to GitHub repo. Build metadata captured at compile time (same pattern tonearm validated).

---

## Phase L — onboarding flow

Goal: a clean first-run flow that gets the user from install to first book playing. **Voice analog:** `:features:onboarding`.

- [ ] **L.1** Welcome screen — one short sentence + one CTA.
- [ ] **L.2** Permissions screen — `POST_NOTIFICATIONS` (API 33+); SAF permission is granted by the picker, not by a Manifest-level dialog, so this screen is short.
- [ ] **L.3** Folder picker screen — explains the four `FolderType` modes briefly with a small visual for each, then launches the SAF `OPEN_DOCUMENT_TREE` flow, then asks the user to pick the type.
- [ ] **L.4** First-scan loading — shows scan progress (books found / chapters parsed). Skippable to library after first book lands.
- [ ] **L.5** Onboarding-completed flag in DataStore. Re-running clears it (debug menu only).

---

## Phase M — home-screen widget

Goal: now-playing widget. **Voice analog:** `:features:widget`.

- [ ] **M.1** Glance widget (`androidx.glance.appwidget`) — book cover, book title, chapter title, transport (prev / play-pause / next).
- [ ] **M.2** Tap on cover → open the player screen.
- [ ] **M.3** Configurable size variants (1×1, 2×2, 4×1).

---

## Phase N — Android Auto

Goal: the browse tree shows up in Android Auto, voice search resolves to a book and starts playback. **Voice analog:** `:core:playback` (`LibrarySessionCallback`, `BookSearchHandler`, `BookSearchParser`, `VoiceSearch`, `CustomCommand`).

This is the payoff for picking `MediaLibraryService` in Phase B.2 instead of the simpler `MediaSessionService`.

- [ ] **N.1** Manifest declarations — `automotiveApp`, allowed-package metadata for `com.google.android.projection.gearhead`.
- [ ] **N.2** `LibrarySessionCallback.onGetLibraryRoot` returns the root browseable node.
- [ ] **N.3** `onGetChildren` walks: Root → "Currently listening" / "Not started" / "All books" / "Authors". Each leaf is a `MediaItem` with a `BookId` that `onPlaybackResumption` can re-hydrate.
- [ ] **N.4** Voice search: parse `query` for artist/album/title — `BookSearchParser` resolves to a `BookId` + position (resume where you left off, not from the beginning).
- [ ] **N.5** Custom commands: set sleep timer, change speed, skip silence — surfaced as Auto custom action buttons.
- [ ] **N.6** Verify via Desktop Head Unit (DHU) on the dev machine (`~/Android/Sdk/extras/google/auto/desktop-head-unit`).

---

## Phase O — release / Obtainium / GitHub Actions

Goal: a sideload-able APK on GitHub Releases. Mirror of tonearm's release pipeline almost exactly. **Voice analog:** none — Voice ships via Play + F-Droid.

- [ ] **O.1** `scripts/build-release-apk.sh` — port from tonearm, swap `tonearm` → `lectern` in artefact names + tag prefixes.
- [ ] **O.2** `.github/workflows/release.yml` — tag-only, self-disabling, mirror of tonearm's workflow. Zero CI minutes by default.
- [ ] **O.3** First release `v0.1.0-<sha7>` — debug-signed, sideload via Obtainium, validate the install path end-to-end.
- [ ] **O.4** Production-signed releases when keystore is in place — env vars `LECTERN_RELEASE_KEYSTORE` / `LECTERN_RELEASE_KEY_ALIAS` / `LECTERN_RELEASE_KEY_PASSWORD`.

---

## Phase P — polish + edge cases (the unglamorous one)

Caught-once-then-fixed bugs and gnarly real-world cases that don't belong to any earlier phase. Lectern will accumulate these the same way tonearm did; this is the catch-bucket so they don't get filed under a wrong phase.

- [ ] **P.1** Cold-start playback resumption — `MediaSession.Callback.onPlaybackResumption` returns last book + position. Verified via process-death simulation (`adb shell am force-stop com.eight87.lectern` while playing).
- [ ] **P.2** Headset / Bluetooth controls — play, pause, prev-chapter (long-press prev), next-chapter (long-press next). Verified on AVD virtual headset + real BT headset.
- [ ] **P.3** Process death during scan — partial scan is committed as a Room transaction per-book; killing the process mid-scan loses only the in-flight book.
- [ ] **P.4** SAF permission revoked under us — when a tree's `Uri` becomes unreadable, all books from that tree are marked `active = false` (soft-delete) and a banner on the library screen offers to re-pick.
- [ ] **P.5** Very large libraries — Voice has #3175 (OOM on huge libraries). Profile lectern at 500 books, 10000 chapters; if Room queries on the library screen are blocking, page with `Pager` from `androidx.paging`.
- [ ] **P.6** Foreground service lifecycle on Android 14+ — `FOREGROUND_SERVICE_MEDIA_PLAYBACK` strictness around when the service can start in the background. Verify on API 34+ AVD.
- [ ] **P.7** Position-save cadence — Voice's "experimental playback persistence" (26.4.3) saves on events not continuously, for battery. Mirror the pattern: save on chapter-end, on pause, on backgrounding, on shutdown — not on a 1Hz timer.

---

## Stretch — post-1.0 differentiators (planned, not scoped to v1)

These are the differentiators identified in the Voice deconstruction (`docs/plans/sharing-analysis.md` and the README "non-goals" list). **Not v1.** Listed here so they don't fall out of the surface area when v1 ships.

- **S.1** Cross-device position sync — optional, BYO-server (WebDAV / Nextcloud) or first-class Audiobookshelf integration. Top recurring community ask on Voice.
- **S.2** Series detection + smart shelves — auto-detect series from filename / metadata, group, "next in series", listening stats.
- **S.3** Standalone Wear OS app — offline-sync a chapter to the watch, leave the phone behind.
- **S.4** Chromecast — Cast a book to a speaker.
- **S.5** Smart-resume — when resuming after >24h, rewind to the last sentence boundary via on-device VAD (whisper-tiny or simpler).
- **S.6** OPDS catalogue browsing for public-domain audiobooks (LibriVox, Standard Ebooks).
- **S.7** Chapter editor — inline rename / split / merge of chapters, write-back as a sidecar `chapters.txt` (no mutation of the original M4B).

---

## Subagent dispatching template

Each subagent prompt for a lectern phase should include:

```
You are picking up Phase <X> of lectern's main build plan.

Read these files first, in order:
1. /home/laragana/workspace/lectern/CLAUDE.md
2. /home/laragana/workspace/lectern/docs/plans/main.md (find Phase <X>)
3. /home/laragana/workspace/lectern/docs/plans/sharing-analysis.md (skim — relevant if your phase touches a candidate-shareable surface)
4. The Voice analog if mentioned in the phase header (browse-only via WebFetch — do not vendor any code).

Your job:
- Land sub-steps <X.1> through <X.N>.
- Tick each `- [ ]` to `- [x]` IN THE SAME COMMIT that ships the work.
- Add `**Shipped:** <X.1>–<X.N> in commit <id>.` to the phase footer when all sub-steps are ticked.
- Do not opportunistically refactor other phases.
- Do not modify ~/.claude/ files.
- Consult `android docs search <q>` and the `android-skills` MCP before general web search for Android API questions.
```
