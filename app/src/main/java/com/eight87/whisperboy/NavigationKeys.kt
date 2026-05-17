package com.eight87.whisperboy

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute : NavKey

/**
 * Full-screen player surface (Phase F.1) for a specific book.
 *
 * Retired from the user-driven nav path: the book-tap in [com.eight87.whisperboy.ui.library.LibraryScreen]
 * now fires `bookCommands.playBook(bookId)` and animates the in-place
 * [com.eight87.whisperboy.ui.playback.NowPlayingSheet] from peek (mini-player) to fully
 * expanded, NOT a back-stack push. Retained for future deeplink entry — e.g.
 * notification tap, Android Auto launch — which will hand off to the sheet's
 * `animateTo(1f)` rather than register a nav entry. See tonearmboy's
 * `pendingDeeplink` pattern in `TonearmboyApp.kt` for the shape that lands when
 * deeplinks ship.
 */
@Serializable data class PlaybackRoute(val bookId: String) : NavKey

/**
 * Per-book DuckDuckGo image-search surface for cover art (cover-art.md Phase B). Pushed
 * onto the back stack by the library long-press action sheet's "Search online" item; the
 * library-side integration ships in a coexisting inch.
 */
@Serializable data class CoverSearchRoute(val bookId: String) : NavKey

/**
 * Phase K.1 — settings root surface. Pushed from the library top-app-bar
 * overflow menu's "Settings" item. Hosts the category list that branches
 * into Playback (K.2) / Sleep timer (K.3) / Library (K.4) / Theme (K.5) /
 * About (K.6) sub-pages.
 */
@Serializable data object SettingsRoute : NavKey

/**
 * Phase K.6 — About sub-page. Pushed from `SettingsRoute`. Shows app
 * icon, version, license, clean-room "spiritual sibling" credit for
 * Voice, GitHub link, and (eventually) the open-source-licenses
 * sub-page tracked in `docs/plans/oss-licenses.md`.
 */
@Serializable data object AboutRoute : NavKey

/**
 * oss-licenses Phase B — Open-source licenses sub-page. Pushed from
 * `AboutRoute`. Renders the build-time Licensee inventory shipped at
 * `assets/licenses/artifacts.json` plus the per-SPDX license bodies
 * at `assets/licenses/<spdx>.txt`.
 */
@Serializable data object LicensesRoute : NavKey

/**
 * Phase K.4 — Library settings hub. Pushed from Settings. Hosts the
 * configured folder roots (list + add FAB + remove) plus rows that
 * navigate to the three sub-screens: [LibrarySortDefaultRoute],
 * [LibraryGridModeDefaultRoute], [LibraryScanFiltersRoute].
 *
 * Route name preserved (was `LibraryFoldersRoute` in the K.4 partial)
 * so persisted nav stacks survive the rename — see the renamed
 * `LibrarySettingsScreen.kt` for the hub composable.
 */
@Serializable data object LibraryFoldersRoute : NavKey

/**
 * Phase K.4 sub-screen — Default sort. Radio group over [BookSortKey].
 * Reads/writes `LibraryUiSettings.sortKey` (same source of truth as the
 * library toolbar selection — no separate "default vs current").
 */
@Serializable data object LibrarySortDefaultRoute : NavKey

/**
 * Phase K.4 sub-screen — Default grid mode. Radio group over [GridMode].
 * Reads/writes `LibraryUiSettings.gridMode` (same source of truth as
 * the library toolbar grid-toggle).
 */
@Serializable data object LibraryGridModeDefaultRoute : NavKey

/**
 * Phase K.4 sub-screen — Scan filters. Checkbox list of supported audio
 * extensions; toggles drive `LibraryScanFilterSettings.disabledExtensions`.
 * After a change, the screen calls
 * `libraryRescanCoordinator.requestRescan(force = true)` so the disabled
 * extension is excluded from the library on the next scan.
 */
@Serializable data object LibraryScanFiltersRoute : NavKey

/**
 * Phase K.5 — Theme sub-page. Pushed from Settings. Hosts the theme-mode
 * radio group (Light / Dark / Follow system) plus the dynamic-color
 * (Material You) toggle. Selection persists via [com.eight87.whisperboy.data.theme.ThemeSettings].
 */
@Serializable data object ThemeSettingsRoute : NavKey

/**
 * Phase K.2 — Playback sub-page. Pushed from Settings. Hosts the
 * [com.eight87.whisperboy.data.playback.PlaybackSettings] knobs: per-book
 * defaults (speed / skip silence / gain), seek seconds (rewind / forward /
 * auto-rewind radio groups), and a system equalizer launcher row that
 * fires `AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL`.
 */
@Serializable data object PlaybackSettingsRoute : NavKey

/**
 * Phase K.3 — Sleep timer sub-page. Pushed from Settings. Hosts the four
 * [com.eight87.whisperboy.data.playback.SleepTimerSettings] knobs: default
 * duration (5/10/15/30/45/60 min radio group), fade-out duration (slider),
 * shake-to-resume (toggle), and the auto-arm window (two `LocalTime`
 * chips + clear button).
 */
@Serializable data object SleepTimerSettingsRoute : NavKey

/**
 * Phase L — first-run onboarding flow. Three steps, in order:
 *
 * 1. [OnboardingWelcomeRoute] — one short sentence + Get-started CTA.
 * 2. [OnboardingPermissionsRoute] — `POST_NOTIFICATIONS` rationale (API 33+; auto-skip otherwise).
 * 3. [OnboardingFolderPickerRoute] — explains the four `FolderType` modes, launches
 *    the SAF `OPEN_DOCUMENT_TREE` picker, surfaces the FolderType bottom sheet, and
 *    on confirm flips `OnboardingSettings.completed` to `true` + replaces the stack
 *    with `HomeRoute`. The scan runs in the background via `LibraryRescanCoordinator`;
 *    in-library progress is surfaced by `LibraryScanProgressBanner`.
 *
 * The retired `OnboardingFirstScanRoute` previously gated completion on scan-settle —
 * a process-death during the scan trapped users in onboarding forever. See
 * `docs/plans/main.md` L.4 for the post-mortem.
 */
@Serializable data object OnboardingWelcomeRoute : NavKey

@Serializable data object OnboardingPermissionsRoute : NavKey

@Serializable data object OnboardingFolderPickerRoute : NavKey

/**
 * Phase H.1 — bookmark list for a single book. Pushed from the player's top-app-bar
 * "view bookmarks" icon. Tap on a row inside the screen seeks the active player to the
 * bookmark's `positionInBookMs`, auto-resumes playback, and pops the back stack back to the
 * player (the entry-block in `WhisperboyApp` owns the seek + play + pop wiring).
 *
 * Carries `bookId` so the screen can scope its `observeBookmarksForBook` flow without reaching
 * into the playback session — keeps the screen usable even when nothing is currently playing
 * (a future deeplink "bookmarks for book X" pattern, parallel to `PlaybackRoute`).
 */
@Serializable data class BookmarkRoute(val bookId: String) : NavKey

/**
 * R.F.9 — per-author detail screen. Pushed from the library long-press action sheet's
 * "View all by <author>" row. Renders a cover grid filtered on the data layer via
 * [com.eight87.whisperboy.data.library.BookSource.observeBooksByAuthor]; the screen
 * never filters in Compose.
 */
@Serializable data class AuthorDetailRoute(val authorName: String) : NavKey
